package com.nbradbury.wpfolio.data

import com.nbradbury.wpfolio.data.comments.CommentThread
import com.nbradbury.wpfolio.data.db.WpFolioDatabase
import com.nbradbury.wpfolio.data.db.Origin
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.db.SiteEntity
import com.nbradbury.wpfolio.data.db.SiteWithLatest
import com.nbradbury.wpfolio.data.prefs.SettingsStore
import com.nbradbury.wpfolio.data.prefs.WpcomAccount
import com.nbradbury.wpfolio.data.rss.FeedParser
import com.nbradbury.wpfolio.data.rss.FetchResult
import com.nbradbury.wpfolio.data.rss.ParsedItem
import com.nbradbury.wpfolio.data.rss.RssSource
import com.nbradbury.wpfolio.data.wpcom.PageCursor
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.data.wpcom.WpcomMapper
import com.nbradbury.wpfolio.data.wpcom.WpcomPost
import com.nbradbury.wpfolio.data.wpcom.WpcomSource
import com.nbradbury.wpfolio.data.wpcom.WpcomToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class LookupResult(val preview: SitePreview, val subscribedSiteId: String?)

class SessionExpiredException : IOException("Your WordPress.com session expired. Please sign in again.")

/**
 * Single entry point for the UI. Room is the source of truth; WordPress.com (when signed in)
 * and on-device RSS fetching only ever write into it.
 */
class ReaderRepository(
    db: WpFolioDatabase,
    private val settings: SettingsStore,
    private val rss: RssSource,
    private val wpcom: WpcomSource,
) {
    private val sites = db.siteDao()
    private val posts = db.postDao()
    private val refreshMutex = Mutex()
    private var feedCursor: PageCursor? = null

    /** How many feed posts to show; grows as older WordPress.com pages load, so they appear. */
    private val feedLimit = MutableStateFlow(FEED_LIMIT)

    /** VideoPress posters by video guid, so a poster isn't fetched again each time it scrolls into view. */
    private val posters = ConcurrentHashMap<String, String>()

    val account: Flow<WpcomAccount?> = settings.account
    val siteCount: Flow<Int> = sites.observeCount()
    val localSiteCount: Flow<Int> = sites.observeCount(Origin.LOCAL)
    val unreadCount: Flow<Int> = posts.observeUnreadCount()
    val saved: Flow<List<PostEntity>> = posts.observeSaved()
    val liked: Flow<List<PostEntity>> = posts.observeLiked()
    val sitesWithLatest: Flow<List<SiteWithLatest>> = sites.observeAllWithLatest()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun feed(): Flow<List<PostEntity>> = feedLimit.flatMapLatest { posts.observeFeed(it) }
    fun post(id: String): Flow<PostEntity?> = posts.observe(id)

    /** Where [post]'s comments live, or null if the app can't reach them. */
    suspend fun commentThread(post: PostEntity): CommentThread? = CommentThread.of(post, sites.get(post.siteId))
    fun site(id: String): Flow<SiteEntity?> = sites.observe(id)
    fun sitePosts(id: String): Flow<List<PostEntity>> = posts.observeForSite(id)

    /** IDs of the posts in [list], in display order; SITE uses the site of [postId]. */
    suspend fun postIds(list: PostList, postId: String): List<String> = when (list) {
        PostList.FEED -> posts.feedIds(feedLimit.value)
        PostList.SAVED -> posts.savedIds()
        PostList.LIKED -> posts.likedIds()
        PostList.SITE -> posts.get(postId)?.let { posts.siteIds(it.siteId) }.orEmpty()
    }

    /** Refreshes everything. Returns how many sources failed. */
    suspend fun refresh(): Int = refreshMutex.withLock {
        val account = settings.account.first()
        if (account?.isExpired() == true) expireSession()
        // WordPress.com and on-device feeds don't depend on each other, so fetch them together.
        coroutineScope {
            val local = async { refreshLocalSites() }
            val wpcomFailures = if (account == null) 0 else try {
                syncWpcom()
                0
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) expireSession()
                1
            } catch (e: IOException) {
                1
            }
            wpcomFailures + local.await()
        }
    }

    /** Loads older WordPress.com posts. Returns false when there's nothing more to load. */
    suspend fun loadMore(): Boolean {
        val cursor = feedCursor ?: return false
        if (!isSignedIn()) return false
        val page = wpcom.following(cursor)
        mergeWpcom(page.posts)
        feedLimit.update { it + page.posts.size }
        feedCursor = page.next
        return page.next != null
    }

    /**
     * Pulls the signed-in user's likes from WordPress.com, so Liked includes posts liked
     * elsewhere, and clears likes that were removed elsewhere.
     */
    suspend fun syncLiked() {
        if (!isSignedIn()) return
        val likes = wpcom.liked()
        // Many liked posts are old, so they'd otherwise sit in the feed as unread.
        posts.merge(likes.posts.map { it.post.copy(isRead = true) }, likes.posts.associate { it.post.id to true })
        if (likes.complete) posts.unlikeAllExcept(likes.posts.map { it.post.id })
    }

    suspend fun refreshSite(siteId: String) {
        val site = sites.get(siteId) ?: return
        when (site.origin) {
            Origin.WPCOM -> mergeWpcom(wpcom.sitePosts(site))
            Origin.LOCAL -> refreshLocalSite(site, useCache = false)
        }
    }

    suspend fun lookup(input: String): LookupResult {
        val preview = rss.lookup(input)
        return LookupResult(preview, subscribedId(preview.feedUrl, preview.siteUrl))
    }

    /** Searches WordPress.com Reader for sites, marking ones already subscribed to. */
    suspend fun searchSites(query: String): List<SiteSearchResult> =
        markSubscribed(wpcom.searchSites(query))

    /**
     * Sites WordPress.com suggests following, based on the user's likes when signed in, without
     * any already subscribed to here, including on-device feeds.
     */
    suspend fun recommendedSites(): List<SiteSearchResult> =
        markSubscribed(wpcom.recommendedSites()).filter { it.subscribedSiteId == null }

    private suspend fun markSubscribed(results: List<SiteSearchResult>): List<SiteSearchResult> {
        val all = sites.getAll()
        return results.map { it.copy(subscribedSiteId = subscribedId(all, it.feedUrl, it.siteUrl)) }
    }

    /**
     * Loads posts for a site that may not be subscribed to, so it can be previewed, and returns
     * the site id they're stored under. WordPress.com is tried first since it works signed out
     * for WordPress sites; otherwise the feed is fetched on device. The posts belong to no
     * subscription, so the feed ignores them and [pruneOrphanedPosts] clears them later.
     */
    suspend fun loadPreview(target: SiteSearchResult): String {
        val wpSite = previewSite(target, Origin.WPCOM, WpcomMapper.siteKey(target.feedId, target.blogId))
        if (target.feedId != null || target.blogId != null) {
            val loaded = runCatching { wpcom.sitePosts(wpSite) }.getOrDefault(emptyList())
            if (loaded.isNotEmpty()) {
                mergeWpcom(loaded)
                return wpSite.id
            }
        }
        // Stored under the https URL that subscribing records, so the preview's posts carry over.
        val feedUrl = HttpsUpgradeInterceptor.upgrade(target.feedUrl)
        val result = rss.fetch(feedUrl, null, null)
        val localSite = previewSite(target, Origin.LOCAL, feedUrl).copy(feedUrl = feedUrl)
        if (result is FetchResult.Updated) posts.merge(result.feed.items.map { it.toEntity(localSite) })
        return localSite.id
    }

    /** An unsaved site row describing [target], used to label previewed posts. */
    fun previewSite(target: SiteSearchResult, origin: Origin, id: String) = SiteEntity(
        id = id,
        origin = origin,
        name = target.name,
        siteUrl = target.siteUrl,
        feedUrl = target.feedUrl,
        description = target.description,
        iconUrl = target.iconUrl,
        isWordPress = target.isWordPress,
        wpcomFeedId = target.feedId,
        wpcomBlogId = target.blogId,
    )

    suspend fun pruneOrphanedPosts() = posts.deleteOrphans()

    /** A VideoPress video's poster image, or null if it can't be found. */
    suspend fun videoPoster(guid: String): String? =
        posters[guid] ?: runCatching { wpcom.videoPoster(guid) }.getOrNull()?.also { posters[guid] = it }

    /** Subscribes to a search result and returns the new site's id, if it could be identified. */
    suspend fun subscribe(result: SiteSearchResult): String? {
        if (isSignedIn()) return followOnWpcom(result.feedUrl, result.siteUrl)
        return subscribe(rss.lookup(result.feedUrl))
    }

    /** Subscribes and returns the new site's id, if it could be identified. */
    suspend fun subscribe(preview: SitePreview): String? {
        if (isSignedIn()) return followOnWpcom(preview.feedUrl, preview.siteUrl)
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
        return site.id
    }

    private suspend fun followOnWpcom(feedUrl: String, siteUrl: String): String? {
        wpcom.follow(feedUrl)
        val subscriptions = wpcom.subscriptions()
        replaceWpcomSites(subscriptions)
        val site = subscriptions.firstOrNull { sameUrl(it.feedUrl, feedUrl) }
            ?: subscriptions.firstOrNull { sameUrl(it.siteUrl, siteUrl) }
        site?.let { runCatching { mergeWpcom(wpcom.sitePosts(it)) } }
        return site?.id
    }

    suspend fun unsubscribe(siteId: String) {
        val site = sites.get(siteId) ?: return
        if (site.origin == Origin.WPCOM) wpcom.unfollow(site.siteUrl)
        removeSite(siteId)
    }

    suspend fun markRead(postId: String) = posts.markRead(postId)

    suspend fun toggleSaved(postId: String) {
        val post = posts.get(postId) ?: return
        posts.setSaved(postId, !post.isSaved)
    }

    suspend fun toggleLiked(postId: String) {
        val post = posts.get(postId) ?: return
        val liked = !post.isLiked
        posts.setLiked(postId, liked, if (liked) System.currentTimeMillis() else null)
        if (post.canLikeRemotely && isSignedIn()) {
            try {
                wpcom.setLiked(post.wpcomBlogId!!, post.wpcomPostId!!, liked)
            } catch (e: Exception) {
                posts.setLiked(postId, post.isLiked, post.likedAt)
                throw e
            }
        }
    }

    suspend fun signIn(token: WpcomToken) {
        val account = WpcomAccount(token.accessToken, displayName = "", avatarUrl = null, expiresAt = token.expiresAt)
        settings.saveAccount(account)
        settings.setOnboarded()
        val me = wpcom.me()
        settings.saveAccount(account.copy(displayName = me.displayName, avatarUrl = me.avatarUrl))
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
                removeSite(site.id)
                moved++
            }
        }
        refresh()
        return moved
    }

    private suspend fun isSignedIn() = settings.account.first() != null

    private suspend fun expireSession(): Nothing {
        signOut()
        throw SessionExpiredException()
    }

    /** The id of an existing subscription to this feed or site, if there is one. */
    private suspend fun subscribedId(feedUrl: String, siteUrl: String): String? =
        subscribedId(sites.getAll(), feedUrl, siteUrl)

    /**
     * Compares with [sameUrl], like following does, so http vs https, www and a trailing slash
     * don't hide an existing subscription.
     */
    private fun subscribedId(all: List<SiteEntity>, feedUrl: String, siteUrl: String): String? {
        fun matching(url: String) = url.takeIf { it.isNotBlank() }?.let { u ->
            all.firstOrNull { sameUrl(it.feedUrl, u) || sameUrl(it.siteUrl, u) }
        }
        return (matching(feedUrl) ?: matching(siteUrl))?.id
    }

    /** Deletes a subscription and its posts, apart from saved and liked ones. */
    private suspend fun removeSite(id: String) {
        sites.delete(id)
        posts.deleteForSite(id)
    }

    private suspend fun syncWpcom() {
        replaceWpcomSites(wpcom.subscriptions())
        val page = wpcom.following(null)
        mergeWpcom(page.posts)
        feedCursor = page.next
    }

    private suspend fun replaceWpcomSites(subscriptions: List<SiteEntity>) {
        val keep = subscriptions.map { it.id }.toSet()
        for (stale in sites.getByOrigin(Origin.WPCOM).filter { it.id !in keep }) removeSite(stale.id)
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
            posts.merge(withContext(Dispatchers.Default) { result.feed.items.map { it.toEntity(site) } })
            sites.updateCacheHeaders(site.id, result.etag, result.lastModified)
        }
    }

    companion object {
        private const val FEED_LIMIT = 300
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
        wordCount = FeedParser.wordCount(text),
    )
}
