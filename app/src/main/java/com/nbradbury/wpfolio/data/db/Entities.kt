package com.nbradbury.wpfolio.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a subscription lives: fetched on-device, or followed through the WordPress.com Reader. */
enum class Origin { LOCAL, WPCOM }

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    val origin: Origin,
    val name: String,
    val siteUrl: String,
    val feedUrl: String,
    val description: String = "",
    val iconUrl: String? = null,
    val isWordPress: Boolean = false,
    val wpcomFeedId: Long? = null,
    val wpcomBlogId: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val subscribedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "posts",
    indices = [Index("siteId"), Index("publishedAt")],
)
data class PostEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val siteName: String,
    val title: String,
    val excerpt: String,
    val contentHtml: String,
    val url: String,
    val author: String? = null,
    val imageUrl: String? = null,
    val publishedAt: Long,
    val wordCount: Int = 0,
    val wpcomBlogId: Long? = null,
    val wpcomPostId: Long? = null,
    val accent: Int? = null,
    val imageRatio: Float? = null,
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val isLiked: Boolean = false,
) {
    val canLikeRemotely: Boolean get() = wpcomBlogId != null && wpcomPostId != null
}

data class SiteWithLatest(
    @Embedded val site: SiteEntity,
    val latestImage: String?,
    val latestAccent: Int?,
    val latestAt: Long?,
)
