package com.nbradbury.wpfolio.data.comments

import com.nbradbury.wpfolio.data.db.Origin
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.db.SiteEntity

/** A comment as the app shows it, independent of the library that fetched it. */
data class Comment(
    val id: Long,
    /** 0 for a top-level comment. */
    val parentId: Long,
    val author: String,
    val avatarUrl: String?,
    val html: String,
    val postedAt: Long?,
    /** Posted but waiting for the site to approve it. */
    val held: Boolean = false,
)

data class CommentPage(val comments: List<Comment>, val hasMore: Boolean)

/** A comment in reading order; [depth] is 0 for top-level comments and 1 for every reply. */
data class ThreadedComment(val comment: Comment, val depth: Int)

class CommentsException(message: String) : Exception(message)

/** Where a post's comments live. */
sealed interface CommentThread {
    val postId: Long

    /** WordPress.com and Jetpack sites, through WordPress.com's REST API proxy. */
    data class WordPressCom(val blogId: Long, override val postId: Long) : CommentThread

    /** A self-hosted WordPress site's own REST API, read-only. */
    data class SelfHosted(val apiRoot: String, override val postId: Long) : CommentThread

    val canWrite: Boolean get() = this is WordPressCom

    companion object {
        /** Feeds give a WordPress post's ID in its guid: `https://example.com/?p=123`. */
        private val WP_POST_ID = Regex("""[?&]p=(\d+)""")

        /** The post's comment thread, or null when there's no way to reach its comments. */
        fun of(post: PostEntity, site: SiteEntity?): CommentThread? {
            if (post.wpcomBlogId != null && post.wpcomPostId != null) {
                return WordPressCom(post.wpcomBlogId, post.wpcomPostId)
            }
            if (site == null || site.origin != Origin.LOCAL || !site.isWordPress) return null
            val guid = post.id.substringAfter('|')
            val postId = WP_POST_ID.find(guid)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            return SelfHosted(site.siteUrl.trimEnd('/') + "/wp-json/", postId)
        }
    }
}

interface CommentsSource {
    /** One page of [thread]'s approved comments, oldest first. [page] starts at 1. */
    suspend fun list(thread: CommentThread, page: Int): CommentPage

    /** Posts [text] as the signed-in user, as a reply to [parentId] if given. */
    suspend fun create(thread: CommentThread, text: String, parentId: Long?): Comment
}

/**
 * Orders comments for reading: each top-level comment followed by all of its replies, oldest
 * first, one level in. Deeper replies are flattened so they don't drift off a phone screen. A
 * reply whose parent isn't loaded is shown at the top level.
 */
fun threaded(comments: List<Comment>): List<ThreadedComment> {
    val ids = comments.map { it.id }.toSet()
    val replies = comments.groupBy { it.parentId }
    val out = mutableListOf<ThreadedComment>()
    fun addReplies(parentId: Long) {
        for (reply in replies[parentId].orEmpty()) {
            out += ThreadedComment(reply, depth = 1)
            addReplies(reply.id)
        }
    }
    for (comment in comments) {
        if (comment.parentId != 0L && comment.parentId in ids) continue
        out += ThreadedComment(comment, depth = 0)
        addReplies(comment.id)
    }
    return out
}
