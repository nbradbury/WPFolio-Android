package com.nbradbury.folio.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
             xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Second Run &amp; Friends</title>
            <link>https://secondrun.film</link>
            <description>Repertory cinema</description>
            <generator>https://wordpress.org/?v=6.6</generator>
            <item>
              <title>Repertory cinemas are quietly thriving</title>
              <link>https://secondrun.film/2026/10/thriving/</link>
              <guid isPermaLink="false">https://secondrun.film/?p=42</guid>
              <dc:creator><![CDATA[Mara Lind]]></dc:creator>
              <pubDate>Thu, 29 Oct 2026 10:15:00 +0000</pubDate>
              <description><![CDATA[<p>Short summary</p>]]></description>
              <content:encoded><![CDATA[<p>Full text</p><img src="/wp-content/uploads/hero.jpg">]]></content:encoded>
              <media:content url="https://cdn.example.com/media.jpg" medium="image" />
            </item>
            <item>
              <title>Enclosure only</title>
              <link>https://secondrun.film/b/</link>
              <pubDate>Wed, 28 Oct 2026 09:00:00 GMT</pubDate>
              <description>&lt;p&gt;Escaped &lt;img src="https://x.test/inline.png"&gt;&lt;/p&gt;</description>
              <enclosure url="https://cdn.example.com/enc.jpg" type="image/jpeg" length="1" />
            </item>
            <item>
              <title>Inline image</title>
              <link>https://secondrun.film/c/</link>
              <description>&lt;p&gt;Hello &lt;img src="pic.png" width="640"&gt;&lt;/p&gt;</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `parses RSS 2_0 channel and items`() {
        val feed = FeedParser.parse(rss, "https://secondrun.film/feed/")!!
        assertEquals("Second Run & Friends", feed.title)
        assertEquals("https://secondrun.film", feed.siteUrl)
        assertTrue(feed.isWordPress)
        assertEquals(3, feed.items.size)

        val first = feed.items[0]
        assertEquals("https://secondrun.film/?p=42", first.guid)
        assertEquals("Mara Lind", first.author)
        assertEquals("<p>Full text</p><img src=\"/wp-content/uploads/hero.jpg\">", first.contentHtml)
        assertEquals("<p>Short summary</p>", first.summaryHtml)
        assertEquals(Instant.parse("2026-10-29T10:15:00Z").toEpochMilli(), first.publishedAt)
    }

    @Test
    fun `prefers media content, then enclosure, then first inline image resolved against the item link`() {
        val items = FeedParser.parse(rss, "https://secondrun.film/feed/")!!.items
        assertEquals("https://cdn.example.com/media.jpg", items[0].imageUrl)
        assertEquals("https://cdn.example.com/enc.jpg", items[1].imageUrl)
        assertEquals("https://secondrun.film/c/pic.png", items[2].imageUrl)
        assertNull(items[2].publishedAt)
    }

    @Test
    fun `parses Atom feeds`() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Small Web Weekly</title>
              <subtitle>Plain text forever</subtitle>
              <link rel="self" href="https://smallweb.email/feed"/>
              <link rel="alternate" href="https://smallweb.email/"/>
              <entry>
                <title type="html">The case for &lt;em&gt;plain-text&lt;/em&gt; blogging</title>
                <link rel="alternate" href="https://smallweb.email/plain-text"/>
                <id>tag:smallweb.email,2026:1</id>
                <published>2026-10-29T08:00:00-04:00</published>
                <author><name>Ana</name></author>
                <content type="html">&lt;p&gt;Body&lt;/p&gt;&lt;img src="https://smallweb.email/a.webp"&gt;</content>
              </entry>
            </feed>
        """.trimIndent()
        val feed = FeedParser.parse(atom, "https://smallweb.email/feed")!!
        assertEquals("Small Web Weekly", feed.title)
        assertEquals("Plain text forever", feed.description)
        assertEquals("https://smallweb.email/", feed.siteUrl)
        assertFalse(feed.isWordPress)
        val entry = feed.items.single()
        assertEquals("The case for plain-text blogging", entry.title)
        assertEquals("https://smallweb.email/plain-text", entry.link)
        assertEquals("Ana", entry.author)
        assertEquals("https://smallweb.email/a.webp", entry.imageUrl)
        assertEquals(Instant.parse("2026-10-29T12:00:00Z").toEpochMilli(), entry.publishedAt)
    }

    @Test
    fun `returns null for HTML and garbage`() {
        assertNull(FeedParser.parse("<html><body>Hi</body></html>", "https://a.test"))
        assertNull(FeedParser.parse("not xml at all", "https://a.test"))
        assertFalse(FeedParser.looksLikeFeed("<!doctype html><html>"))
        assertTrue(FeedParser.looksLikeFeed("<?xml version=\"1.0\"?>\n<rss version=\"2.0\">"))
    }

    @Test
    fun `parses common date variants`() {
        assertNotNull(FeedParser.parseDate("Thu, 29 Oct 2026 10:15:00 EST"))
        assertNotNull(FeedParser.parseDate("29 Oct 2026 10:15:00 +0100"))
        assertNotNull(FeedParser.parseDate("2026-10-29T10:15:00Z"))
        assertNull(FeedParser.parseDate("yesterday"))
    }
}
