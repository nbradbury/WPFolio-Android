package com.nbradbury.wpfolio.ui.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

sealed interface Block {
    data class Paragraph(val html: String) : Block
    data class Heading(val text: String, val level: Int) : Block
    /** [width] and [height] come from the HTML, so space can be reserved before the image loads. */
    data class Image(val url: String, val caption: String?, val width: Int? = null, val height: Int? = null) : Block {
        val aspectRatio: Float? get() = if (width != null && height != null) width.toFloat() / height else null
    }
    data class Quote(val html: String) : Block
    data class ListItem(val html: String, val marker: String, val depth: Int) : Block
    data class Code(val text: String) : Block
    data object Rule : Block

    /**
     * A video played in place on tap. [id] is the host's video ID, or the file URL for
     * [VideoHost.FILE]; [pageUrl] is where the video lives on the web.
     */
    data class Video(
        val host: VideoHost,
        val id: String,
        val poster: String?,
        val pageUrl: String,
        val width: Int? = null,
        val height: Int? = null,
    ) : Block {
        val aspectRatio: Float get() = if (width != null && height != null) width.toFloat() / height else 16f / 9f
    }

    /** Embedded content that can't be shown natively (maps, audio players, social posts), as a link. */
    data class Embed(val url: String, val title: String?) : Block
}

enum class VideoHost(val label: String) {
    YOUTUBE("YouTube"),
    VIMEO("Vimeo"),
    VIDEOPRESS("VideoPress"),
    FILE("Video"),
}

/** Recognises videos from embed, watch and file URLs. */
object VideoLinks {
    private val YOUTUBE = Regex(
        """(?:youtube(?:-nocookie)?\.com/(?:embed/|shorts/|v/|watch\?(?:[^#]*&)?v=)|youtu\.be/)([A-Za-z0-9_-]+)"""
    )
    private val VIMEO = Regex("""(?:player\.vimeo\.com/video/|//(?:www\.)?vimeo\.com/)(\d+)""")
    private val VIDEOPRESS = Regex("""(?:video\.wordpress\.com|videopress\.com)/(?:embed|v)/([A-Za-z0-9]+)""")
    private val FILE = Regex("""\.(?:mp4|m4v|webm|mov)(?:[?#]|$)""", RegexOption.IGNORE_CASE)

    fun parse(url: String, poster: String? = null, width: Int? = null, height: Int? = null): Block.Video? {
        fun video(host: VideoHost, id: String, poster: String?, page: String) =
            Block.Video(host, id, poster, page, width, height)
        YOUTUBE.find(url)?.groupValues?.get(1)?.let { id ->
            val thumbnail = poster ?: "https://img.youtube.com/vi/$id/hqdefault.jpg"
            return video(VideoHost.YOUTUBE, id, thumbnail, "https://www.youtube.com/watch?v=$id")
        }
        VIMEO.find(url)?.groupValues?.get(1)?.let { id ->
            return video(VideoHost.VIMEO, id, poster, "https://vimeo.com/$id")
        }
        VIDEOPRESS.find(url)?.groupValues?.get(1)?.let { id ->
            return video(VideoHost.VIDEOPRESS, id, poster, "https://videopress.com/v/$id")
        }
        // Only https files: the player can't load cleartext.
        if (url.startsWith("https://") && FILE.containsMatchIn(url)) return video(VideoHost.FILE, url, poster, url)
        return null
    }
}

/**
 * Splits post HTML into blocks that Compose can lay out natively. Inline markup inside a block
 * stays as HTML for AnnotatedString.fromHtml. Videos become play-in-place blocks, other iframes
 * become links, and scripts and forms are dropped.
 */
object ArticleBlocks {
    private val SKIP = setOf(
        "script", "style", "noscript", "audio", "object", "embed", "form", "button", "svg", "input",
    )
    private const val MEDIA = "iframe, video"

    /** Widgets WordPress sometimes leaves in content that aren't part of the post. */
    private val WIDGET_HOSTS = listOf("widgets.wp.com", "jetpack.wordpress.com")
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
                // Inline wrappers such as WordPress's <span class="embed-youtube"> can hold a video.
                node.tagName() in INLINE && node.selectFirst(MEDIA) == null -> inline.append(node.outerHtml())
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
                val images = el.select("img").mapNotNull { image(it, caption = null) }
                val media = el.select(MEDIA).mapNotNull(::media)
                el.select("img, $MEDIA").remove()
                if (el.text().isNotBlank()) out += Block.Paragraph(el.html().trim())
                out += images
                out += media
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> el.text().takeIf { it.isNotBlank() }?.let {
                out += Block.Heading(it, el.tagName()[1].digitToInt())
            }
            "img" -> image(el, el.attr("alt").takeIf { it.isNotBlank() && it.length < 200 })?.let { out += it }
            "iframe", "video" -> media(el)?.let { out += it }
            "figure" -> {
                val caption = el.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() }
                val img = el.selectFirst("img")?.let { image(it, caption) }
                // An embed WordPress couldn't render leaves just its URL in the wrapper.
                val embedUrl = el.takeIf { it.hasClass("wp-block-embed") && it.selectFirst(MEDIA) == null }
                    ?.selectFirst(".wp-block-embed__wrapper")?.text()?.trim()?.takeIf { it.startsWith("http") }
                if (img != null) {
                    out += img
                } else if (embedUrl != null) {
                    out += VideoLinks.parse(embedUrl) ?: Block.Embed(embedUrl, caption)
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

    private fun media(el: Element): Block? {
        // Zero- and one-pixel frames are trackers, not content.
        if (listOf(el.attr("width"), el.attr("height")).any { it == "0" || it == "1" }) return null
        val width = el.attr("width").trim().toIntOrNull()?.takeIf { it > 0 }
        val height = el.attr("height").trim().toIntOrNull()?.takeIf { it > 0 }
        if (el.tagName() == "video") {
            val src = el.absUrl("src").ifEmpty { el.selectFirst("source[src]")?.absUrl("src").orEmpty() }
            return VideoLinks.parse(src, el.absUrl("poster").ifEmpty { null }, width, height)
                ?: src.takeIf { it.startsWith("http") }?.let { Block.Embed(it, null) }
        }
        val src = el.absUrl("src").ifEmpty { el.absUrl("data-src") }
        if (!src.startsWith("http") || WIDGET_HOSTS.any { it in src }) return null
        return VideoLinks.parse(src, width = width, height = height)
            ?: Block.Embed(src, el.attr("title").takeIf { it.isNotBlank() })
    }

    private fun image(img: Element, caption: String?): Block.Image? {
        val (attr, src) = listOf("data-orig-file", "data-src", "data-lazy-src", "src")
            .map { it to img.absUrl(it) }
            .firstOrNull { (_, url) -> url.isNotEmpty() && !url.startsWith("data:") }
            ?: return null
        if (img.attr("width") == "1" || img.attr("height") == "1") return null
        if (TRACKERS.any { it in src }) return null
        // width/height describe the displayed (possibly cropped) size; when loading the original,
        // WordPress's data-orig-size ("w,h") gives that image's real shape.
        val size = img.attr("data-orig-size").split(',').mapNotNull { it.trim().toIntOrNull() }
            .takeIf { attr == "data-orig-file" && it.size == 2 }
            ?: listOf(img.attr("width"), img.attr("height")).map { it.trim().toIntOrNull() }
        val (width, height) = size.map { it?.takeIf { d -> d > 0 } }
        return Block.Image(src, caption, width, height)
    }
}
