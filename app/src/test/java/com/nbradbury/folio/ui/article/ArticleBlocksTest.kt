package com.nbradbury.folio.ui.article

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
            ),
            blocks,
        )
    }
}
