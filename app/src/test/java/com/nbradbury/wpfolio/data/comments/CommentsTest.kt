package com.nbradbury.wpfolio.data.comments

import com.nbradbury.wpfolio.data.db.Origin
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.db.SiteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentsTest {

    @Test
    fun `threads replies under their comment, one level in, in order`() {
        val comments = listOf(
            comment(1), comment(2), comment(3, parent = 1), comment(4, parent = 3), comment(5, parent = 2),
            // Its parent is on a page that hasn't loaded yet.
            comment(6, parent = 99),
        )
        assertEquals(
            listOf(1 to 0, 3 to 1, 4 to 1, 2 to 0, 5 to 1, 6 to 0),
            threaded(comments).map { it.comment.id.toInt() to it.depth },
        )
    }

    @Test
    fun `finds where a post's comments live`() {
        val wpcom = post("wps:5:10", blogId = 5, postId = 10)
        assertEquals(CommentThread.WordPressCom(5, 10), CommentThread.of(wpcom, site = null))

        val local = site(isWordPress = true)
        assertEquals(
            CommentThread.SelfHosted("https://example.com/wp-json/", 123),
            CommentThread.of(post("${local.id}|https://example.com/?p=123"), local),
        )
        // Other sites, and WordPress feeds whose guid doesn't carry the post ID, have no thread.
        assertNull(CommentThread.of(post("${local.id}|https://example.com/?p=123"), site(isWordPress = false)))
        assertNull(CommentThread.of(post("${local.id}|https://example.com/2026/hello/"), local))
    }

    private fun comment(id: Long, parent: Long = 0) =
        Comment(id = id, parentId = parent, author = "A", avatarUrl = null, html = "<p>$id</p>", postedAt = id)

    private fun post(id: String, blogId: Long? = null, postId: Long? = null) = PostEntity(
        id = id, siteId = "https://example.com/feed", siteName = "Example", title = "T", excerpt = "",
        contentHtml = "", url = "https://example.com/p", publishedAt = 1L, wpcomBlogId = blogId, wpcomPostId = postId,
    )

    private fun site(isWordPress: Boolean) = SiteEntity(
        id = "https://example.com/feed", origin = Origin.LOCAL, name = "Example", siteUrl = "https://example.com/",
        feedUrl = "https://example.com/feed", isWordPress = isWordPress,
    )
}
