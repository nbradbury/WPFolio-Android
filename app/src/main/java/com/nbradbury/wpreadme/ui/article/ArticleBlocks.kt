package com.nbradbury.wpreadme.ui.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

sealed interface Block {
    data class Paragraph(val html: String) : Block
    data class Heading(val text: String, val level: Int) : Block
    data class Image(val url: String, val caption: String?) : Block
    data class Quote(val html: String) : Block
    data class ListItem(val html: String, val marker: String, val depth: Int) : Block
    data class Code(val text: String) : Block
    data object Rule : Block
}

/**
 * Splits post HTML into blocks that Compose can lay out natively. Inline markup inside a block
 * stays as HTML for AnnotatedString.fromHtml; embeds and scripts are dropped.
 */
object ArticleBlocks {
    private val SKIP = setOf("script", "style", "noscript", "iframe", "video", "audio", "object", "embed", "form", "button", "svg", "input")
    private val INLINE = setOf("a", "abbr", "b", "br", "cite", "code", "del", "em", "i", "ins", "kbd", "mark", "q", "s", "small", "span", "strong", "sub", "sup", "time", "u")
    private val TRACKERS = listOf("pixel.wp.com", "stats.wordpress.com", "feeds.feedburner.com", "gravatar.com/avatar")

    fun parse(html: String, baseUrl: String): List<Block> {
        if (html.isBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html, baseUrl).body()
        return mutableListOf<Block>().also { walk(body, it, depth = 0) }
    }

    private fun walk(parent: Element, out: MutableList<Block>, depth: Int) {
        val inline = StringBuilder()
        fun flush() {
            val text = inline.toString()
            if (Jsoup.parse(text).text().isNotBlank()) out += Block.Paragraph(text.trim())
            inline.clear()
        }
        for (node in parent.childNodes()) {
            when {
                node is TextNode -> inline.append(node.outerHtml())
                node !is Element -> Unit
                node.tagName() in SKIP -> Unit
                node.tagName() in INLINE -> inline.append(node.outerHtml())
                else -> {
                    flush()
                    block(node, out, depth)
                }
            }
        }
        flush()
    }

    private fun block(el: Element, out: MutableList<Block>, depth: Int) {
        when (el.tagName()) {
            "p" -> {
                val images = el.select("img").mapNotNull(::imageUrl)
                el.select("img").remove()
                if (el.text().isNotBlank()) out += Block.Paragraph(el.html().trim())
                images.forEach { out += Block.Image(it, null) }
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> el.text().takeIf { it.isNotBlank() }?.let {
                out += Block.Heading(it, el.tagName()[1].digitToInt())
            }
            "img" -> imageUrl(el)?.let { out += Block.Image(it, el.attr("alt").takeIf { a -> a.isNotBlank() && a.length < 200 }) }
            "figure" -> {
                val img = el.selectFirst("img")?.let(::imageUrl)
                if (img != null) {
                    out += Block.Image(img, el.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() })
                } else {
                    walk(el, out, depth)
                }
            }
            "blockquote" -> {
                val paragraphs = el.select("p")
                val html = if (paragraphs.isEmpty()) el.html() else paragraphs.joinToString("<br><br>") { it.html() }
                if (Jsoup.parse(html).text().isNotBlank()) out += Block.Quote(html.trim())
            }
            "ul", "ol" -> el.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
                val nested = li.children().filter { it.tagName() == "ul" || it.tagName() == "ol" }
                nested.forEach { it.remove() }
                if (li.text().isNotBlank()) {
                    out += Block.ListItem(li.html().trim(), if (el.tagName() == "ol") "${i + 1}." else "•", depth)
                }
                nested.forEach { block(it, out, depth + 1) }
            }
            "pre" -> out += Block.Code(el.wholeText().trimEnd())
            "hr" -> out += Block.Rule
            else -> walk(el, out, depth)
        }
    }

    private fun imageUrl(img: Element): String? {
        val src = listOf("data-orig-file", "data-src", "data-lazy-src", "src")
            .map { img.absUrl(it) }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
            ?: return null
        if (img.attr("width") == "1" || img.attr("height") == "1") return null
        if (TRACKERS.any { it in src }) return null
        return src
    }
}
