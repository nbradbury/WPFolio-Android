package com.nbradbury.wpfolio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query(
        """
        SELECT s.*,
            (SELECT p.imageUrl FROM posts p WHERE p.siteId = s.id AND p.imageUrl IS NOT NULL
                ORDER BY p.publishedAt DESC LIMIT 1) AS latestImage,
            (SELECT p.accent FROM posts p WHERE p.siteId = s.id AND p.imageUrl IS NOT NULL
                ORDER BY p.publishedAt DESC LIMIT 1) AS latestAccent,
            (SELECT MAX(p.publishedAt) FROM posts p WHERE p.siteId = s.id) AS latestAt
        FROM sites s
        ORDER BY latestAt IS NULL, latestAt DESC, s.name COLLATE NOCASE
        """
    )
    fun observeAllWithLatest(): Flow<List<SiteWithLatest>>

    @Query("SELECT * FROM sites WHERE id = :id")
    fun observe(id: String): Flow<SiteEntity?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun get(id: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE origin = :origin")
    suspend fun getByOrigin(origin: Origin): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE feedUrl = :url OR siteUrl = :url LIMIT 1")
    suspend fun findByUrl(url: String): SiteEntity?

    @Query("SELECT COUNT(*) FROM sites")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sites WHERE origin = :origin")
    fun observeCount(origin: Origin): Flow<Int>

    @Upsert
    suspend fun upsert(site: SiteEntity)

    @Upsert
    suspend fun upsertAll(sites: List<SiteEntity>)

    @Query("UPDATE sites SET etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun updateCacheHeaders(id: String, etag: String?, lastModified: String?)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts WHERE siteId IN (SELECT id FROM sites) ORDER BY publishedAt DESC LIMIT :limit")
    fun observeFeed(limit: Int): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE siteId = :siteId ORDER BY publishedAt DESC")
    fun observeForSite(siteId: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isSaved = 1 ORDER BY publishedAt DESC")
    fun observeSaved(): Flow<List<PostEntity>>

    @Query("SELECT COUNT(*) FROM posts WHERE isRead = 0 AND siteId IN (SELECT id FROM sites)")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM posts WHERE id = :id")
    fun observe(id: String): Flow<PostEntity?>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun get(id: String): PostEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(post: PostEntity): Long

    @Query(
        """
        UPDATE posts SET siteName = :siteName, title = :title, excerpt = :excerpt, contentHtml = :contentHtml,
            url = :url, author = :author, imageUrl = :imageUrl, wordCount = :wordCount,
            publishedAt = CASE WHEN :publishedAt = 0 THEN publishedAt ELSE :publishedAt END,
            accent = CASE WHEN imageUrl IS :imageUrl THEN accent ELSE NULL END,
            imageRatio = CASE WHEN imageUrl IS :imageUrl THEN imageRatio ELSE NULL END,
            isLiked = COALESCE(:liked, isLiked)
        WHERE id = :id
        """
    )
    suspend fun updateContent(
        id: String,
        siteName: String,
        title: String,
        excerpt: String,
        contentHtml: String,
        url: String,
        author: String?,
        imageUrl: String?,
        publishedAt: Long,
        wordCount: Int,
        liked: Boolean?,
    )

    /**
     * Inserts new posts and refreshes the content of existing ones without touching the
     * user's read/saved state. [remoteLikes] carries server-side like state for WP.com posts.
     * Undated posts (publishedAt = 0) are stamped with the time they were first seen.
     */
    @Transaction
    suspend fun merge(posts: List<PostEntity>, remoteLikes: Map<String, Boolean> = emptyMap()) {
        val now = System.currentTimeMillis()
        for (post in posts) {
            val dated = if (post.publishedAt == 0L) post.copy(publishedAt = now) else post
            if (insertIgnore(dated) == -1L) {
                updateContent(
                    id = post.id,
                    siteName = post.siteName,
                    title = post.title,
                    excerpt = post.excerpt,
                    contentHtml = post.contentHtml,
                    url = post.url,
                    author = post.author,
                    imageUrl = post.imageUrl,
                    publishedAt = post.publishedAt,
                    wordCount = post.wordCount,
                    liked = remoteLikes[post.id],
                )
            }
        }
    }

    @Query("SELECT * FROM posts WHERE accent IS NULL AND imageUrl IS NOT NULL ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun needingAccent(limit: Int): List<PostEntity>

    @Query("UPDATE posts SET accent = :accent, imageRatio = :ratio WHERE imageUrl = :imageUrl")
    suspend fun setAccent(imageUrl: String, accent: Int, ratio: Float?)

    @Query("UPDATE posts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE posts SET isSaved = :saved WHERE id = :id")
    suspend fun setSaved(id: String, saved: Boolean)

    @Query("UPDATE posts SET isLiked = :liked WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("DELETE FROM posts WHERE siteId = :siteId AND isSaved = 0")
    suspend fun deleteForSite(siteId: String)

    @Query("DELETE FROM posts WHERE siteId LIKE 'wp:%' AND isSaved = 0")
    suspend fun deleteWpcom()
}
