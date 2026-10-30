package com.nbradbury.wpreadme.data

import com.nbradbury.wpreadme.data.db.WpReadMeDatabase
import com.nbradbury.wpreadme.data.db.Origin
import com.nbradbury.wpreadme.data.db.PostEntity
import com.nbradbury.wpreadme.data.db.SiteEntity
import com.nbradbury.wpreadme.data.db.SiteWithLatest
import com.nbradbury.wpreadme.data.prefs.SettingsStore
import com.nbradbury.wpreadme.data.prefs.WpcomAccount
import com.nbradbury.wpreadme.data.rss.FetchResult
import com.nbradbury.wpreadme.data.rss.ParsedItem
import com.nbradbury.wpreadme.data.rss.RssSource
import com.nbradbury.wpreadme.data.wpcom.PageCursor
import com.nbradbury.wpreadme.data.wpcom.WpcomPost
import com.nbradbury.wpreadme.data.wpcom.WpcomSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.io.IOException

data class LookupResult(val preview: SitePreview, val subscribedSiteId: String?)

class SessionExpiredException : IOException("Your WordPress.com session expired. Please sign in again.")

/**
 * Single entry point for the UI. Room is the source of truth; WordPress.com (when signed in)
 * and on-device RSS fetching only ever write into it.
 */
class ReaderRepository(
    db: WpReadMeDatabase,
    private val settings: SettingsStore,
    private val rss: RssSource,
    private val wpcom: WpcomSource,
    private val onPostsChanged: () -> Unit = {},
) {
    private val sites = db.siteDao()
    private val posts = db.postDao()
    private val refreshMutex = Mutex()
    private var feedCursor: PageCursor? = null

    val account: Flow<WpcomAccount?> = settings.account
    val siteCount: Flow<Int> = sites.observeCount()
    val localSiteCount: Flow<Int> = sites.observeCount(Origin.LOCAL)
    val unreadCount: Flow<Int> = posts.observeUnreadCount()
    val saved: Flow<List<PostEntity>> = posts.observeSaved()
    val sitesWithLatest: Flow<List<SiteWithLatest>> = sites.observeAllWithLatest()

    fun feed(limit: Int = FEED_LIMIT): Flow<List<PostEntity>> = posts.observeFeed(limit)
    fun post(id: String): Flow<PostEntity?> = posts.observe(id)
    fun site(id: String): Flow<SiteEntity?> = sites.observe(id)
    fun sitePosts(id: String): Flow<List<PostEntity>> = posts.observeForSite(id)

    /** Refreshes everything. Returns how many sources failed. */
    suspend fun refresh(): Int = refreshMutex.withLock {
        var failures = 0
        if (isSignedIn()) {
            try {
                syncWpcom()
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    signOut()
                    throw SessionExpiredException()
                }
                failures++
            } catch (e: IOException) {
                failures++
            }
        }
        failures += refreshLocalSites()
        onPostsChanged()
        failures
    }

    /** Loads older WordPress.com posts. Returns false when there's nothing more to load. */
    suspend fun loadMore(): Boolean {
        val cursor = feedCursor ?: return false
        if (!isSignedIn()) return false
        val page = wpcom.following(cursor)
        mergeWpcom(page.posts)
        feedCursor = page.next
        onPostsChanged()
        return page.next != null
    }

    suspend fun refreshSite(siteId: String) {
        val site = sites.get(siteId) ?: return
        when (site.origin) {
            Origin.WPCOM -> mergeWpcom(wpcom.sitePosts(site))
            Origin.LOCAL -> refreshLocalSite(site, useCache = false)
        }
        onPostsChanged()
    }

    suspend fun lookup(input: String): LookupResult {
        val preview = rss.lookup(input)
        val existing = sites.findByUrl(preview.feedUrl) ?: sites.findByUrl(preview.siteUrl)
        return LookupResult(preview, existing?.id)
    }

    /** Subscribes and returns the new site's id, if it could be identified. */
    suspend fun subscribe(preview: SitePreview): String? {
        if (isSignedIn()) {
            wpcom.follow(preview.feedUrl)
            val subscriptions = wpcom.subscriptions()
            replaceWpcomSites(subscriptions)
            val site = subscriptions.firstOrNull { sameUrl(it.feedUrl, preview.feedUrl) }
                ?: subscriptions.firstOrNull { sameUrl(it.siteUrl, preview.siteUrl) }
            site?.let { runCatching { mergeWpcom(wpcom.sitePosts(it)) } }
            onPostsChanged()
            return site?.id
        }
        val site = SiteEntity(
            id = preview.feedUrl,
            origin = Origin.LOCAL,
            name = preview.name,
            siteUrl = preview.siteUrl,
            feedUrl = preview.feedUrl,
            description = preview.description,
            isWordPress = preview.isWordPress,
        )
        sites.upsert(site)
        posts.merge(preview.items.map { it.toEntity(site) })
        onPostsChanged()
        return site.id
    }

    suspend fun unsubscribe(siteId: String) {
        val site = sites.get(siteId) ?: return
        if (site.origin == Origin.WPCOM) wpcom.unfollow(site.siteUrl)
        sites.delete(siteId)
        posts.deleteForSite(siteId)
    }

    suspend fun markRead(postId: String) = posts.markRead(postId)

    suspend fun toggleSaved(postId: String) {
        val post = posts.get(postId) ?: return
        posts.setSaved(postId, !post.isSaved)
    }

    suspend fun toggleLiked(postId: String) {
        val post = posts.get(postId) ?: return
        val liked = !post.isLiked
        posts.setLiked(postId, liked)
        if (post.canLikeRemotely && isSignedIn()) {
            try {
                wpcom.setLiked(post.wpcomBlogId!!, post.wpcomPostId!!, liked)
            } catch (e: Exception) {
                posts.setLiked(postId, !liked)
                throw e
            }
        }
    }

    suspend fun signIn(token: String) {
        settings.saveAccount(WpcomAccount(token, displayName = "", avatarUrl = null))
        val me = wpcom.me()
        settings.saveAccount(WpcomAccount(token, me.displayName, me.avatarUrl))
        refresh()
    }

    suspend fun signOut() {
        settings.clearAccount()
        feedCursor = null
        for (site in sites.getByOrigin(Origin.WPCOM)) sites.delete(site.id)
        posts.deleteWpcom()
    }

    /** Follows every on-device feed through WordPress.com so they sync across devices. */
    suspend fun moveLocalFeedsToWpcom(): Int {
        var moved = 0
        for (site in sites.getByOrigin(Origin.LOCAL)) {
            runCatching { wpcom.follow(site.feedUrl) }.onSuccess {
                sites.delete(site.id)
                posts.deleteForSite(site.id)
                moved++
            }
        }
        refresh()
        return moved
    }

    private suspend fun isSignedIn() = settings.account.first() != null

    private suspend fun syncWpcom() {
        replaceWpcomSites(wpcom.subscriptions())
        val page = wpcom.following(null)
        mergeWpcom(page.posts)
        feedCursor = page.next
    }

    private suspend fun replaceWpcomSites(subscriptions: List<SiteEntity>) {
        val keep = subscriptions.map { it.id }.toSet()
        for (stale in sites.getByOrigin(Origin.WPCOM).filter { it.id !in keep }) {
            sites.delete(stale.id)
            posts.deleteForSite(stale.id)
        }
        sites.upsertAll(subscriptions)
    }

    private suspend fun mergeWpcom(wpcomPosts: List<WpcomPost>) {
        posts.merge(wpcomPosts.map { it.post }, wpcomPosts.associate { it.post.id to it.liked })
    }

    private suspend fun refreshLocalSites(): Int = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLEL_FETCHES)
        sites.getByOrigin(Origin.LOCAL).map { site ->
            async {
                semaphore.withPermit {
                    runCatching { refreshLocalSite(site, useCache = true) }.isFailure
                }
            }
        }.awaitAll().count { it }
    }

    private suspend fun refreshLocalSite(site: SiteEntity, useCache: Boolean) {
        val result = rss.fetch(
            site.feedUrl,
            etag = site.etag.takeIf { useCache },
            lastModified = site.lastModified.takeIf { useCache },
        )
        if (result is FetchResult.Updated) {
            posts.merge(result.feed.items.map { it.toEntity(site) })
            sites.updateCacheHeaders(site.id, result.etag, result.lastModified)
        }
    }

    companion object {
        const val FEED_LIMIT = 300
        private const val MAX_PARALLEL_FETCHES = 6

        fun sameUrl(a: String, b: String): Boolean = normalize(a) == normalize(b)

        private fun normalize(url: String) =
            url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }
}

internal fun ParsedItem.toEntity(site: SiteEntity): PostEntity {
    val html = contentHtml.ifBlank { summaryHtml }
    val text = Jsoup.parse(html).text()
    val summaryText = if (summaryHtml.isNotBlank()) Jsoup.parse(summaryHtml).text() else text
    return PostEntity(
        id = "${site.id}|${guid.ifBlank { link.ifBlank { title } }}",
        siteId = site.id,
        siteName = site.name,
        title = title.ifBlank { summaryText.take(80) },
        excerpt = summaryText.take(300),
        contentHtml = html,
        url = link,
        author = author,
        imageUrl = imageUrl,
        publishedAt = publishedAt ?: 0L,
        wordCount = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size,
    )
}
