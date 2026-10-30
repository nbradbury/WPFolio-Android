package com.nbradbury.wpfolio.data.wpcom

import com.nbradbury.wpfolio.data.db.SiteEntity
import java.time.Instant

data class WpcomPage(val posts: List<WpcomPost>, val next: PageCursor?)

/** Either the API's own page handle or, when it doesn't send one, the oldest date seen. */
sealed interface PageCursor {
    data class Handle(val value: String) : PageCursor
    data class Before(val iso: String) : PageCursor
}

interface WpcomSource {
    suspend fun me(): WpcomMe
    suspend fun subscriptions(): List<SiteEntity>
    suspend fun following(cursor: PageCursor?): WpcomPage
    suspend fun sitePosts(site: SiteEntity): List<WpcomPost>
    suspend fun searchSites(query: String): List<SiteSearchResult>
    suspend fun follow(url: String)
    suspend fun unfollow(url: String)
    suspend fun setLiked(blogId: Long, postId: Long, liked: Boolean)
}

class RetrofitWpcomSource(private val api: WpcomApi) : WpcomSource {

    override suspend fun me(): WpcomMe = WpcomMapper.me(api.me())

    override suspend fun subscriptions(): List<SiteEntity> {
        val all = mutableListOf<SiteEntity>()
        var page = 1
        while (true) {
            val batch = WpcomMapper.subscriptions(api.subscriptions(page = page, number = PAGE_SIZE))
            all += batch
            if (batch.size < PAGE_SIZE || page >= MAX_PAGES) break
            page++
        }
        return all.distinctBy { it.id }
    }

    override suspend fun following(cursor: PageCursor?): WpcomPage {
        val json = api.following(
            number = FEED_PAGE_SIZE,
            pageHandle = (cursor as? PageCursor.Handle)?.value,
            before = (cursor as? PageCursor.Before)?.iso,
        )
        val posts = WpcomMapper.posts(json)
        val next = WpcomMapper.nextPageHandle(json)?.let { PageCursor.Handle(it) }
            ?: posts.minOfOrNull { it.post.publishedAt }?.let { PageCursor.Before(Instant.ofEpochMilli(it).toString()) }
        return WpcomPage(posts, next.takeIf { posts.isNotEmpty() })
    }

    override suspend fun sitePosts(site: SiteEntity): List<WpcomPost> {
        val json = when {
            site.wpcomBlogId != null -> api.sitePosts(site.wpcomBlogId)
            site.wpcomFeedId != null -> api.feedPosts(site.wpcomFeedId)
            else -> return emptyList()
        }
        return WpcomMapper.posts(json, siteIdOverride = site.id)
    }

    override suspend fun searchSites(query: String): List<SiteSearchResult> =
        WpcomMapper.searchResults(api.searchFeeds(query))

    override suspend fun follow(url: String) {
        api.follow(url)
    }

    override suspend fun unfollow(url: String) {
        api.unfollow(url)
    }

    override suspend fun setLiked(blogId: Long, postId: Long, liked: Boolean) {
        if (liked) api.like(blogId, postId) else api.unlike(blogId, postId)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 10
        const val FEED_PAGE_SIZE = 30
    }
}
