package com.nbradbury.folio.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiscoveryTest {

    @Test
    fun `finds advertised feeds, skipping comment feeds, and detects WordPress`() {
        val html = """
            <html><head>
              <title>Altitude Notes – Essays</title>
              <meta name="description" content="Essays from a hut kitchen">
              <link rel="alternate" type="application/rss+xml" href="/feed/">
              <link rel="alternate" type="application/rss+xml" href="/comments/feed/">
              <link rel="alternate" type="application/atom+xml" href="https://altitudenotes.blog/feed/atom/">
              <link rel="https://api.w.org/" href="https://altitudenotes.blog/wp-json/">
            </head><body></body></html>
        """.trimIndent()
        val page = FeedDiscovery.parse(html, "https://altitudenotes.blog/")
        assertEquals(
            listOf("https://altitudenotes.blog/feed/", "https://altitudenotes.blog/feed/atom/"),
            page.feedUrls,
        )
        assertTrue(page.isWordPress)
        assertEquals("Essays from a hut kitchen", page.description)
    }

    @Test
    fun `plain sites aren't WordPress`() {
        val page = FeedDiscovery.parse("<html><head><title>Hi</title></head></html>", "https://a.test/")
        assertTrue(page.feedUrls.isEmpty())
        assertFalse(page.isWordPress)
        assertEquals("Hi", page.title)
    }

    @Test
    fun `normalizes typed URLs`() {
        assertEquals("https://secondrun.film", OkHttpRssSource.normalizeUrl(" secondrun.film "))
        assertEquals("http://a.test/feed", OkHttpRssSource.normalizeUrl("http://a.test/feed"))
        assertEquals(null, OkHttpRssSource.normalizeUrl("not a url"))
        assertEquals(null, OkHttpRssSource.normalizeUrl("localhost"))
    }
}
