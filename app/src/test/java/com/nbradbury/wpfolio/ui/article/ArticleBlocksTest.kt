package com.nbradbury.wpfolio.ui.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleBlocksTest {

    @Test
    fun `splits post HTML into native blocks`() {
        val html = """
            Intro <em>text</em>
            <h2>Heading</h2>
            <p>Para with <a href="/x">link</a><img src="/inline.jpg"></p>
            <figure><img src="https://i.test/a.jpg"><figcaption>Caption</figcaption></figure>
            <blockquote><p>One</p><p>Two</p></blockquote>
            <ul><li>A<ul><li>A1</li></ul></li><li>B</li></ul>
            <pre>code()</pre>
            <hr>
            <iframe src="https://youtube.com/embed/x"></iframe>
            <img src="https://pixel.wp.com/t.gif" width="1" height="1">
        """.trimIndent()
        val blocks = ArticleBlocks.parse(html, "https://site.test/post/")
        assertEquals(
            listOf(
                Block.Paragraph("Intro <em>text</em>"),
                Block.Heading("Heading", 2),
                Block.Paragraph("Para with <a href=\"/x\">link</a>"),
                Block.Image("https://site.test/inline.jpg", null),
                Block.Image("https://i.test/a.jpg", "Caption"),
                Block.Quote("One<br><br>Two"),
                Block.ListItem("A", "•", 0),
                Block.ListItem("A1", "•", 1),
                Block.ListItem("B", "•", 0),
                Block.Code("code()"),
                Block.Rule,
                Block.Video(
                    VideoHost.YOUTUBE, "x", "https://img.youtube.com/vi/x/hqdefault.jpg", "https://www.youtube.com/watch?v=x",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `keeps image dimensions so space can be reserved`() {
        val html = """
            <img src="https://a.test/plain.jpg" width="800" height="600">
            <img src="https://a.test/thumb-150x150.jpg" width="150" height="150"
                 data-orig-file="https://a.test/full.jpg" data-orig-size="1600,900">
            <img src="https://a.test/unsized.jpg" width="100%">
        """.trimIndent()
        val images = ArticleBlocks.parse(html, "https://a.test/").filterIsInstance<Block.Image>()
        assertEquals(4f / 3f, images[0].aspectRatio!!, 0.001f)
        // Loading the original, so its shape wins over the cropped thumbnail's.
        assertEquals("https://a.test/full.jpg", images[1].url)
        assertEquals(16f / 9f, images[1].aspectRatio!!, 0.001f)
        assertEquals(null, images[2].aspectRatio)
    }

    @Test
    fun `groups WordPress galleries into grids`() {
        val html = """
            <figure class="wp-block-gallery has-nested-images columns-default is-cropped">
              <figure class="wp-block-image"><img src="https://a.test/1.jpg"><figcaption>One</figcaption></figure>
              <figure class="wp-block-image"><img src="https://a.test/2.jpg"></figure>
              <figcaption class="blocks-gallery-caption">Trip</figcaption>
            </figure>
            <div class="gallery gallery-columns-5">
              <dl class="gallery-item"><dt><img src="https://a.test/3.jpg"></dt><dd class="gallery-caption">Three</dd></dl>
              <dl class="gallery-item"><dt><img src="https://a.test/4.jpg"></dt></dl>
              <dl class="gallery-item"><dt><img src="https://a.test/5.jpg"></dt></dl>
            </div>
            <div class="wp-block-jetpack-tiled-gallery"><div class="tiled-gallery__gallery">
              <figure class="tiled-gallery__item"><img src="https://a.test/6.jpg"></figure>
            </div></div>
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Gallery(
                    listOf(Block.Image("https://a.test/1.jpg", "One"), Block.Image("https://a.test/2.jpg", null)),
                    columns = 2,
                    caption = "Trip",
                ),
                Block.Gallery(
                    listOf(
                        Block.Image("https://a.test/3.jpg", "Three"),
                        Block.Image("https://a.test/4.jpg", null),
                        Block.Image("https://a.test/5.jpg", null),
                    ),
                    columns = 3,
                    caption = null,
                ),
                // A one-image gallery is just an image.
                Block.Image("https://a.test/6.jpg", null),
            ),
            ArticleBlocks.parse(html, "https://a.test/"),
        )
    }

    @Test
    fun `spots excerpts by how they end, not by length`() {
        fun excerpt(html: String) = ArticleBlocks.looksLikeExcerpt(ArticleBlocks.parse(html, "https://a.test/"))
        // WordPress summary feeds, and other common "there's more" endings.
        assertEquals(true, excerpt("<p>The first fifty-five words of the post [&#8230;]</p>"))
        assertEquals(true, excerpt("<p>Opening lines...</p>"))
        assertEquals(true, excerpt("""<p>Opening lines. <a href="/post">Continue reading &rarr;</a></p>"""))
        assertEquals(true, excerpt(""))
        // Short complete posts aren't summaries.
        assertEquals(false, excerpt("<p>This post has a code block.</p><pre>code()</pre>"))
        assertEquals(false, excerpt("<p>Short… but complete.</p>"))
        assertEquals(false, excerpt("""<p>Caption above</p><img src="https://a.test/1.jpg">"""))
    }

    @Test
    fun `turns WordPress video embeds into playable blocks`() {
        // Markup as it appears in WordPress.com feeds.
        val html = """
            <figure class="wp-block-embed is-type-video is-provider-youtube"><div class="wp-block-embed__wrapper">
              <iframe class="youtube-player" width="640" height="360" src="https://www.youtube.com/embed/kdw0QmlFTtA?version=3&amp;rel=1"></iframe>
            </div></figure>
            <figure class="wp-block-jetpack-videopress"><div class="jetpack-video-wrapper">
              <iframe title="VideoPress Video Player" width="500" height="281" src="https://video.wordpress.com/embed/NzPMA2MA?cover=1"></iframe>
            </div></figure>
            <p>Watch: <span class="embed-youtube"><iframe src="//www.youtube.com/embed/eEBv0STiYhI?rel=1"></iframe></span></p>
            <figure class="wp-block-video"><video poster="https://a.test/p.jpg"><source src="https://a.test/clip.mp4"></video></figure>
            <figure class="wp-block-embed is-provider-vimeo"><div class="wp-block-embed__wrapper">https://vimeo.com/76979871</div></figure>
        """.trimIndent()
        val blocks = ArticleBlocks.parse(html, "https://a.test/post/")
        val videos = blocks.filterIsInstance<Block.Video>()
        assertEquals(listOf("kdw0QmlFTtA", "NzPMA2MA", "eEBv0STiYhI", "https://a.test/clip.mp4", "76979871"), videos.map { it.id })
        assertEquals(
            listOf(VideoHost.YOUTUBE, VideoHost.VIDEOPRESS, VideoHost.YOUTUBE, VideoHost.FILE, VideoHost.VIMEO),
            videos.map { it.host },
        )
        assertEquals(640f / 360f, videos[0].aspectRatio, 0.001f)
        assertEquals(16f / 9f, videos[2].aspectRatio, 0.001f)
        assertEquals("https://a.test/p.jpg", videos[3].poster)
        // The text around an inline embed stays as a paragraph.
        val paragraph = blocks.filterIsInstance<Block.Paragraph>().single()
        assertEquals("Watch:", org.jsoup.Jsoup.parse(paragraph.html).text())
    }

    @Test
    fun `other iframes become link cards and widgets are dropped`() {
        val html = """
            <iframe title="Map of the trail" src="https://maps.example.com/embed?x=1"></iframe>
            <iframe src="https://widgets.wp.com/likes/index.html"></iframe>
            <iframe src="https://tracker.example.com/t" width="1" height="1"></iframe>
            <video src="http://insecure.test/clip.mp4"></video>
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Embed("https://maps.example.com/embed?x=1", "Map of the trail"),
                Block.Embed("http://insecure.test/clip.mp4", null),
            ),
            ArticleBlocks.parse(html, "https://a.test/"),
        )
    }

    @Test
    fun `recognises video page and short links`() {
        assertEquals("abc_-123", VideoLinks.parse("https://youtu.be/abc_-123")?.id)
        assertEquals("abc", VideoLinks.parse("https://www.youtube.com/watch?feature=share&v=abc")?.id)
        assertEquals("abc", VideoLinks.parse("https://youtube.com/shorts/abc")?.id)
        assertEquals(null, VideoLinks.parse("https://example.com/watch?v=abc"))
        assertEquals(null, VideoLinks.parse("http://a.test/clip.mp4"))
    }
}
