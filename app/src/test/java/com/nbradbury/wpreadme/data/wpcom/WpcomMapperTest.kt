package com.nbradbury.wpreadme.data.wpcom

import com.nbradbury.wpreadme.data.db.Origin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WpcomMapperTest {

    @Test
    fun `maps subscriptions with string IDs and site or feed meta`() {
        val json = Json.parseToJsonElement(
            """
            {"subscriptions":[
              {"ID":"1","blog_ID":"123","feed_ID":"456","URL":"https://altitudenotes.blog",
               "meta":{"data":{"site":{"name":"Altitude &amp; Notes","description":"Hut essays","icon":{"img":"https://i.test/icon.png"}},
                               "feed":{"name":"ignored","feed_URL":"https://altitudenotes.blog/feed/"}}}},
              {"ID":"2","blog_ID":"0","feed_ID":789,"URL":"https://secondrun.film/rss",
               "meta":{"data":{"feed":{"name":"Second Run","feed_URL":"https://secondrun.film/rss"}}}},
              {"ID":"3"}
            ]}
            """
        ).jsonObject
        val sites = WpcomMapper.subscriptions(json)
        assertEquals(2, sites.size)

        val wp = sites[0]
        assertEquals("wp:456", wp.id)
        assertEquals(Origin.WPCOM, wp.origin)
        assertEquals("Altitude & Notes", wp.name)
        assertEquals("https://altitudenotes.blog/feed/", wp.feedUrl)
        assertEquals("https://i.test/icon.png", wp.iconUrl)
        assertTrue(wp.isWordPress)
        assertEquals(123L, wp.wpcomBlogId)

        val rss = sites[1]
        assertEquals("wp:789", rss.id)
        assertEquals("Second Run", rss.name)
        assertFalse(rss.isWordPress)
        assertNull(rss.wpcomBlogId)
    }

    @Test
    fun `maps posts, falling back through image fields and ignoring false strings`() {
        val json = Json.parseToJsonElement(
            """
            {"posts":[
              {"ID":10,"site_ID":123,"feed_ID":456,"title":"It&#8217;s quiet","excerpt":"<p>Short</p>",
               "content":"<p>Body</p>","URL":"https://altitudenotes.blog/q","author":{"name":"Mara"},
               "date":"2026-10-29T10:00:00+00:00","featured_image":"","featured_media":{"uri":"https://i.test/m.jpg","type":"image"},
               "i_like":true,"site_name":"Altitude Notes","word_count":"1200","is_external":false},
              {"ID":5,"site_ID":0,"feed_ID":789,"feed_item_ID":99,"title":"External","content":"<img src='https://i.test/c.png'>",
               "URL":"https://secondrun.film/x","date":"2026-10-28T10:00:00Z","featured_image":false,"is_external":true}
            ],"next_page_handle":"abc"}
            """
        ).jsonObject
        val posts = WpcomMapper.posts(json)
        assertEquals("abc", WpcomMapper.nextPageHandle(json))

        val (wp, liked) = posts[0]
        assertEquals("wps:123:10", wp.id)
        assertEquals("wp:456", wp.siteId)
        assertEquals("It’s quiet", wp.title)
        assertEquals("Short", wp.excerpt)
        assertEquals("https://i.test/m.jpg", wp.imageUrl)
        assertEquals(1200, wp.wordCount)
        assertTrue(wp.canLikeRemotely)
        assertTrue(liked)

        val external = posts[1].post
        assertEquals("wpf:789:99", external.id)
        assertEquals("https://i.test/c.png", external.imageUrl)
        assertFalse(external.canLikeRemotely)
        assertFalse(posts[1].liked)
    }

    @Test
    fun `site override keeps site-feed posts attached to their subscription`() {
        val json = Json.parseToJsonElement("""{"posts":[{"ID":1,"site_ID":123,"title":"t","date":"2026-10-29T10:00:00Z"}]}""").jsonObject
        assertEquals("wp:456", WpcomMapper.posts(json, siteIdOverride = "wp:456").single().post.siteId)
    }
}
