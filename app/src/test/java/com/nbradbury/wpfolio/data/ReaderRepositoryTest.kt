package com.nbradbury.wpfolio.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nbradbury.wpfolio.data.db.WpFolioDatabase
import com.nbradbury.wpfolio.data.db.Origin
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.db.SiteEntity
import com.nbradbury.wpfolio.data.prefs.SettingsStore
import com.nbradbury.wpfolio.data.prefs.WpcomAccount
import com.nbradbury.wpfolio.data.rss.FetchResult
import com.nbradbury.wpfolio.data.rss.ParsedFeed
import com.nbradbury.wpfolio.data.rss.ParsedItem
import com.nbradbury.wpfolio.data.rss.RssSource
import com.nbradbury.wpfolio.data.wpcom.PageCursor
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.data.wpcom.WpcomLikes
import com.nbradbury.wpfolio.data.wpcom.WpcomMe
import com.nbradbury.wpfolio.data.wpcom.WpcomPage
import com.nbradbury.wpfolio.data.wpcom.WpcomPost
import com.nbradbury.wpfolio.data.wpcom.WpcomSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderRepositoryTest {
    private lateinit var db: WpFolioDatabase
    private lateinit var settings: SettingsStore
    private val rss = FakeRss()
    private val wpcom = FakeWpcom()
    private lateinit var repository: ReaderRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WpFolioDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
        settings.clearAccount()
        repository = ReaderRepository(db, settings, rss, wpcom)
    }

    @After
    fun tearDown() = runBlocking {
        settings.clearAccount()
        db.close()
    }

    @Test
    fun `signed out, subscribing stores a local site with its preview posts`() = runBlocking {
        val id = repository.subscribe(preview(items = listOf(item("1", "First"))))

        val site = db.siteDao().get(id!!)!!
        assertEquals(Origin.LOCAL, site.origin)
        assertEquals("First", repository.feed().first().single().title)
        assertTrue(wpcom.followed.isEmpty())
    }

    @Test
    fun `refresh updates content but keeps read and saved state`() = runBlocking {
        val id = repository.subscribe(preview(items = listOf(item("1", "Old title"))))!!
        val postId = repository.feed().first().single().id
        repository.markRead(postId)
        repository.toggleSaved(postId)

        rss.result = FetchResult.Updated(feed(listOf(item("1", "New title"))), etag = "e1", lastModified = null)
        assertEquals(0, repository.refresh())

        val post = repository.feed().first().single()
        assertEquals("New title", post.title)
        assertTrue(post.isRead)
        assertTrue(post.isSaved)
        assertEquals("e1", db.siteDao().get(id)!!.etag)
    }

    @Test
    fun `failed local fetches are counted, not thrown`() = runBlocking {
        repository.subscribe(preview(items = emptyList()))
        rss.fail = true
        assertEquals(1, repository.refresh())
    }

    @Test
    fun `signed in, subscribing follows through WordPress_com`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.subscriptions = listOf(wpcomSite("wp:7", feedUrl = "https://secondrun.film/rss"))

        val id = repository.subscribe(preview(feedUrl = "https://secondrun.film/rss"))

        assertEquals(listOf("https://secondrun.film/rss"), wpcom.followed)
        assertEquals("wp:7", id)
        assertEquals(Origin.WPCOM, db.siteDao().get("wp:7")!!.origin)
    }

    @Test
    fun `unsubscribing a WordPress_com site unfollows remotely but local sites don't`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.subscriptions = listOf(wpcomSite("wp:7", siteUrl = "https://secondrun.film"))
        repository.refresh()
        repository.unsubscribe("wp:7")
        assertEquals(listOf("https://secondrun.film"), wpcom.unfollowed)
        assertNull(db.siteDao().get("wp:7"))

        settings.clearAccount()
        val local = repository.subscribe(preview(feedUrl = "https://a.test/feed"))!!
        repository.unsubscribe(local)
        assertEquals(1, wpcom.unfollowed.size)
    }

    @Test
    fun `an unauthorized sync signs the user out`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.unauthorized = true
        val error = runCatching { repository.refresh() }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
        assertNull(settings.account.first())
    }

    @Test
    fun `an expired implicit-flow token signs the user out without calling the API`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null, expiresAt = System.currentTimeMillis() - 1))
        val error = runCatching { repository.refresh() }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
        assertNull(settings.account.first())
    }

    @Test
    fun `search marks sites that are already subscribed`() = runBlocking {
        repository.subscribe(preview(feedUrl = "https://a.test/feed"))
        wpcom.searchResults = listOf(searchResult("https://a.test/feed"), searchResult("https://b.test/feed"))
        val results = repository.searchSites("test")
        assertEquals("https://a.test/feed", results[0].subscribedSiteId)
        assertNull(results[1].subscribedSiteId)
    }

    @Test
    fun `signed in, subscribing to a search result follows its feed on WordPress_com`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.subscriptions = listOf(wpcomSite("wp:9", feedUrl = "http://b.test/feed"))
        assertEquals("wp:9", repository.subscribe(searchResult("http://b.test/feed")))
        assertEquals(listOf("http://b.test/feed"), wpcom.followed)
        assertTrue(rss.lookups.isEmpty())
    }

    @Test
    fun `signed out, subscribing to a search result fetches the feed, trying https first`() = runBlocking {
        val id = repository.subscribe(searchResult("http://b.test/feed"))
        assertEquals(listOf("https://b.test/feed", "http://b.test/feed"), rss.lookups)
        assertEquals(Origin.LOCAL, db.siteDao().get(id!!)!!.origin)
    }

    @Test
    fun `previewing a WordPress_com site loads its posts without subscribing`() = runBlocking {
        val post = PostEntity(id = "wps:5:1", siteId = "", siteName = "B", title = "Hi", excerpt = "", contentHtml = "",
            url = "https://b.test/hi", publishedAt = 1L)
        wpcom.sitePosts = listOf(WpcomPost(post, liked = false))
        val siteId = repository.loadPreview(searchResult("http://b.test/feed").copy(feedId = 9, blogId = 5))

        assertEquals("wp:9", siteId)
        assertEquals(listOf("Hi"), repository.sitePosts(siteId).first().map { it.title })
        assertNull(db.siteDao().get(siteId))
        assertTrue(repository.feed().first().isEmpty())
    }

    @Test
    fun `previewing a plain feed fetches it on device, trying https first`() = runBlocking {
        rss.result = FetchResult.Updated(feed(listOf(item("1", "Feed post"))), etag = null, lastModified = null)
        val siteId = repository.loadPreview(searchResult("http://b.test/feed"))

        assertEquals(listOf("https://b.test/feed", "http://b.test/feed"), rss.fetches)
        assertEquals("http://b.test/feed", siteId)
        assertEquals(listOf("Feed post"), repository.sitePosts(siteId).first().map { it.title })
    }

    @Test
    fun `pruning removes unsaved preview posts but keeps saved, liked and subscribed ones`() = runBlocking {
        repository.subscribe(preview(feedUrl = "https://a.test/feed", items = listOf(item("1", "Subscribed"))))
        rss.result = FetchResult.Updated(feed(listOf(item("2", "Previewed"), item("3", "Kept"), item("4", "Liked"))), null, null)
        val previewId = repository.loadPreview(searchResult("http://b.test/feed"))
        val previewed = repository.sitePosts(previewId).first()
        repository.toggleSaved(previewed.single { it.title == "Kept" }.id)
        repository.toggleLiked(previewed.single { it.title == "Liked" }.id)

        repository.pruneOrphanedPosts()

        assertEquals(setOf("Kept", "Liked"), repository.sitePosts(previewId).first().map { it.title }.toSet())
        assertEquals(listOf("Subscribed"), repository.feed().first().map { it.title })
    }

    @Test
    fun `post IDs follow the list the post was opened from`() = runBlocking {
        val newer = item("2", "Newer").copy(publishedAt = 2_000L)
        repository.subscribe(preview(feedUrl = "https://a.test/feed", items = listOf(item("1", "Older"), newer)))
        repository.subscribe(preview(feedUrl = "https://c.test/feed", items = listOf(item("3", "Other").copy(publishedAt = 3_000L))))
        val feed = repository.feed().first()
        val older = feed.single { it.title == "Older" }.id

        assertEquals(listOf("Other", "Newer", "Older"), feed.map { it.title })
        assertEquals(feed.map { it.id }, repository.postIds(PostList.FEED, older))
        assertEquals(
            feed.filter { it.siteId == "https://a.test/feed" }.map { it.id },
            repository.postIds(PostList.SITE, older),
        )
        repository.toggleSaved(older)
        assertEquals(listOf(older), repository.postIds(PostList.SAVED, older))
        repository.toggleLiked(older)
        assertEquals(listOf(older), repository.postIds(PostList.LIKED, older))
    }

    @Test
    fun `syncing likes adds posts liked elsewhere and clears likes removed elsewhere`() = runBlocking {
        repository.subscribe(preview(feedUrl = "https://a.test/feed", items = listOf(item("1", "Local"))))
        repository.toggleLiked(repository.feed().first().single().id)
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        db.postDao().merge(listOf(wpcomPost("wps:5:1", "Unliked on the web").copy(isLiked = true)))
        wpcom.likes = WpcomLikes(listOf(WpcomPost(wpcomPost("wps:5:2", "Liked on the web"), liked = true)), complete = true)

        repository.syncLiked()

        val liked = repository.liked.first()
        assertEquals(setOf("Local", "Liked on the web"), liked.map { it.title }.toSet())
        // Fetched likes aren't new to the user, so they don't count as unread.
        assertTrue(liked.single { it.id == "wps:5:2" }.isRead)
    }

    @Test
    fun `liked posts are ordered by when they were liked`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.likes = WpcomLikes(
            listOf(
                WpcomPost(wpcomPost("wps:5:1", "Old post").copy(publishedAt = 1L, likedAt = 3_000L), liked = true),
                WpcomPost(wpcomPost("wps:5:2", "New post").copy(publishedAt = 2_000L, likedAt = 100L), liked = true),
            ),
            complete = true,
        )
        repository.syncLiked()
        assertEquals(listOf("Old post", "New post"), repository.liked.first().map { it.title })

        // Liking again puts it back on top.
        repository.toggleLiked("wps:5:2")
        repository.toggleLiked("wps:5:2")
        assertEquals(listOf("New post", "Old post"), repository.liked.first().map { it.title })
    }

    @Test
    fun `a partial list of likes doesn't clear any`() = runBlocking {
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        db.postDao().merge(listOf(wpcomPost("wps:5:1", "Older like").copy(isLiked = true)))
        wpcom.likes = WpcomLikes(listOf(WpcomPost(wpcomPost("wps:5:2", "Newer like"), liked = true)), complete = false)

        repository.syncLiked()

        assertEquals(setOf("Older like", "Newer like"), repository.liked.first().map { it.title }.toSet())
    }

    @Test
    fun `signing out removes WordPress_com sites but keeps local ones`() = runBlocking {
        repository.subscribe(preview(feedUrl = "https://a.test/feed"))
        settings.saveAccount(WpcomAccount("token", "Nick", null))
        wpcom.subscriptions = listOf(wpcomSite("wp:7"))
        repository.refresh()
        assertEquals(2, repository.siteCount.first())

        repository.signOut()
        assertEquals(1, repository.siteCount.first())
        assertFalse(db.siteDao().getByOrigin(Origin.LOCAL).isEmpty())
    }

    private fun wpcomPost(id: String, title: String) = PostEntity(
        id = id, siteId = "wp:9", siteName = "B", title = title, excerpt = "", contentHtml = "",
        url = "https://b.test/$id", publishedAt = 1L, wpcomBlogId = 5, wpcomPostId = id.substringAfterLast(':').toLong(),
    )

    private fun item(guid: String, title: String) = ParsedItem(
        guid = guid, title = title, link = "https://a.test/$guid", author = null,
        publishedAt = 1_000L, summaryHtml = "<p>$title</p>", contentHtml = "", imageUrl = null,
    )

    private fun feed(items: List<ParsedItem>) =
        ParsedFeed(title = "A", siteUrl = "https://a.test", description = "", generator = null, items = items)

    private fun preview(feedUrl: String = "https://a.test/feed", items: List<ParsedItem> = emptyList()) = SitePreview(
        name = "A", description = "", siteUrl = "https://a.test", feedUrl = feedUrl, isWordPress = false, items = items,
    )

    private fun searchResult(feedUrl: String) = SiteSearchResult(
        name = "B", siteUrl = feedUrl.substringBeforeLast("/feed"), feedUrl = feedUrl,
        description = "", iconUrl = null, isWordPress = true,
    )

    private fun wpcomSite(id: String, siteUrl: String = "https://wp.test", feedUrl: String = "$siteUrl/feed") = SiteEntity(
        id = id, origin = Origin.WPCOM, name = "WP", siteUrl = siteUrl, feedUrl = feedUrl, wpcomFeedId = 7,
    )

    private class FakeRss : RssSource {
        var result: FetchResult = FetchResult.NotModified
        var fail = false
        val fetches = mutableListOf<String>()
        override suspend fun fetch(feedUrl: String, etag: String?, lastModified: String?): FetchResult {
            fetches += feedUrl
            if (fail || feedUrl.startsWith("https://b.test")) throw java.io.IOException("offline")
            return result
        }
        val lookups = mutableListOf<String>()
        override suspend fun lookup(input: String): SitePreview {
            lookups += input
            if (input.startsWith("https://")) throw java.io.IOException("no https")
            return SitePreview("A", "", "https://a.test", input, isWordPress = false, items = emptyList())
        }
    }

    private class FakeWpcom : WpcomSource {
        var subscriptions: List<SiteEntity> = emptyList()
        var unauthorized = false
        val followed = mutableListOf<String>()
        val unfollowed = mutableListOf<String>()

        override suspend fun me() = WpcomMe("Nick", null)
        override suspend fun subscriptions(): List<SiteEntity> {
            if (unauthorized) throw HttpException(Response.error<Any>(401, "".toResponseBody()))
            return subscriptions
        }
        override suspend fun following(cursor: PageCursor?) = WpcomPage(emptyList(), null)
        var likes = WpcomLikes(emptyList(), complete = true)
        override suspend fun liked() = likes
        var sitePosts: List<WpcomPost> = emptyList()
        override suspend fun sitePosts(site: SiteEntity): List<WpcomPost> =
            sitePosts.map { it.copy(post = it.post.copy(siteId = site.id)) }
        var searchResults: List<SiteSearchResult> = emptyList()
        override suspend fun searchSites(query: String) = searchResults
        override suspend fun videoPoster(guid: String): String? = null
        override suspend fun follow(url: String) { followed += url }
        override suspend fun unfollow(url: String) { unfollowed += url }
        override suspend fun setLiked(blogId: Long, postId: Long, liked: Boolean) = Unit
    }
}
