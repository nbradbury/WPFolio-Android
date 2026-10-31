package com.nbradbury.wpfolio.nav

import com.nbradbury.wpfolio.data.PostList
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object FeedRoute
@Serializable data object SitesRoute
@Serializable data class AddRoute(val fromOnboarding: Boolean = false)
@Serializable data class ArticleRoute(val postId: String, val list: PostList = PostList.FEED)
@Serializable data class SiteRoute(val siteId: String)

/** Saved or Liked posts. */
@Serializable data class PostListRoute(val list: PostList)

/** A site that may not be subscribed to yet, opened from Add's search results or URL preview. */
@Serializable
data class SitePreviewRoute(
    val name: String,
    val siteUrl: String,
    val feedUrl: String,
    val description: String,
    val iconUrl: String? = null,
    val isWordPress: Boolean,
    // 0 means unknown; WordPress.com IDs are always positive.
    val feedId: Long = 0,
    val blogId: Long = 0,
) {
    fun toResult() = SiteSearchResult(
        name = name,
        siteUrl = siteUrl,
        feedUrl = feedUrl,
        description = description,
        iconUrl = iconUrl,
        isWordPress = isWordPress,
        feedId = feedId.takeIf { it > 0 },
        blogId = blogId.takeIf { it > 0 },
    )
}

fun SiteSearchResult.toPreviewRoute() = SitePreviewRoute(
    name = name,
    siteUrl = siteUrl,
    feedUrl = feedUrl,
    description = description,
    iconUrl = iconUrl,
    isWordPress = isWordPress,
    feedId = feedId ?: 0,
    blogId = blogId ?: 0,
)
