package com.nbradbury.wpreadme.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nbradbury.wpreadme.data.db.WpReadMeDatabase
import com.nbradbury.wpreadme.data.db.Origin
import com.nbradbury.wpreadme.data.db.SiteEntity
import com.nbradbury.wpreadme.data.prefs.SettingsStore
import com.nbradbury.wpreadme.data.prefs.WpcomAccount
import com.nbradbury.wpreadme.data.rss.FetchResult
import com.nbradbury.wpreadme.data.rss.ParsedFeed
import com.nbradbury.wpreadme.data.rss.ParsedItem
import com.nbradbury.wpreadme.data.rss.RssSource
import com.nbradbury.wpreadme.data.wpcom.PageCursor
import com.nbradbury.wpreadme.data.wpcom.WpcomMe
import com.nbradbury.wpreadme.data.wpcom.WpcomPage
import com.nbradbury.wpreadme.data.wpcom.WpcomPost
import com.nbradbury.wpreadme.data.wpcom.WpcomSource
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
    private lateinit var db: WpReadMeDatabase
    private lateinit var settings: SettingsStore
    private val rss = FakeRss()
    private val wpcom = FakeWpcom()
    private lateinit var repository: ReaderRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WpReadMeDatabase::class.java).allowMainThreadQueries().build()
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

    private fun item(guid: String, title: String) = ParsedItem(
        guid = guid, title = title, link = "https://a.test/$guid", author = null,
        publishedAt = 1_000L, summaryHtml = "<p>$title</p>", contentHtml = "", imageUrl = null,
    )

    private fun feed(items: List<ParsedItem>) =
        ParsedFeed(title = "A", siteUrl = "https://a.test", description = "", generator = null, items = items)

    private fun preview(feedUrl: String = "https://a.test/feed", items: List<ParsedItem> = emptyList()) = SitePreview(
        name = "A", description = "", siteUrl = "https://a.test", feedUrl = feedUrl, isWordPress = false, items = items,
    )

    private fun wpcomSite(id: String, siteUrl: String = "https://wp.test", feedUrl: String = "$siteUrl/feed") = SiteEntity(
        id = id, origin = Origin.WPCOM, name = "WP", siteUrl = siteUrl, feedUrl = feedUrl, wpcomFeedId = 7,
    )

    private class FakeRss : RssSource {
        var result: FetchResult = FetchResult.NotModified
        var fail = false
        override suspend fun fetch(feedUrl: String, etag: String?, lastModified: String?): FetchResult {
            if (fail) throw java.io.IOException("offline")
            return result
        }
        override suspend fun lookup(input: String): SitePreview = error("unused")
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
        override suspend fun sitePosts(site: SiteEntity): List<WpcomPost> = emptyList()
        override suspend fun follow(url: String) { followed += url }
        override suspend fun unfollow(url: String) { unfollowed += url }
        override suspend fun setLiked(blogId: Long, postId: Long, liked: Boolean) = Unit
    }
}
