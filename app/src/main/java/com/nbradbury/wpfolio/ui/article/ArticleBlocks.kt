package com.nbradbury.wpfolio.ui.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import com.nbradbury.wpfolio.data.rss.FeedParser
import java.net.URLDecoder
import java.net.URLEncoder

sealed interface Block {
    data class Paragraph(val html: String) : Block
    data class Heading(val text: String, val level: Int) : Block
    /** [width] and [height] come from the HTML, so space can be reserved before the image loads. */
    data class Image(val url: String, val caption: String?, val width: Int? = null, val height: Int? = null) : Block {
        val aspectRatio: Float? get() = if (width != null && height != null) width.toFloat() / height else null
    }
    /** A WordPress gallery, shown as a grid of [columns]. [caption] belongs to the whole gallery. */
    data class Gallery(val images: List<Image>, val columns: Int, val caption: String?) : Block
    /** [citation] is the quote's attribution; [pull] marks a pullquote, which stands apart from the text. */
    data class Quote(val html: String, val citation: String? = null, val pull: Boolean = false) : Block
    /** Rows of cells as inline HTML; the first [headerRows] rows are headings. */
    data class Table(val rows: List<List<String>>, val headerRows: Int, val caption: String?) : Block
    data class ListItem(val html: String, val marker: String, val depth: Int) : Block
    data class Code(val text: String) : Block
    data object Rule : Block
    /** A WordPress button block: a call to action that opens [url]. */
    data class Button(val label: String, val url: String) : Block
    /** A `<details>` section, collapsed to its [summary] until tapped. */
    data class Details(val summary: String, val blocks: List<Block>) : Block

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
    private val CELLS = setOf("td", "th")
    /** Images that are content, not emoji drawn inline in text. */
    private const val CONTENT_IMAGE = "img:not(.wp-smiley):not(.emoji)"

    /** Block-editor, classic shortcode and Jetpack tiled galleries. */
    private val GALLERIES = setOf("wp-block-gallery", "gallery", "tiled-gallery", "wp-block-jetpack-tiled-gallery")
    private const val GALLERY_ITEM = "figure, .gallery-item, .tiled-gallery-item"
    private val GALLERY_COLUMNS = Regex("""^(?:gallery-)?columns-(\d+)$""")
    private const val MAX_GALLERY_COLUMNS = 3

    /** Widgets WordPress sometimes leaves in content that aren't part of the post. */
    private val WIDGET_HOSTS = listOf("widgets.wp.com", "jetpack.wordpress.com")
    private val INLINE = setOf("a", "abbr", "b", "br", "cite", "code", "del", "em", "i", "ins", "kbd", "mark", "q", "s", "small", "span", "strong", "sub", "sup", "time", "u")

    private val TRUNCATED = Regex("""(?:…|\.\.\.|continue reading|read more)\W*$""", RegexOption.IGNORE_CASE)

    /**
     * Whether [blocks] look like a feed's excerpt rather than the whole post: nothing at all, or
     * text that trails off with an ellipsis or a "read more" link, as WordPress's summary feeds do.
     */
    fun looksLikeExcerpt(blocks: List<Block>): Boolean {
        if (blocks.isEmpty()) return true
        val last = blocks.last() as? Block.Paragraph ?: return false
        return TRUNCATED.containsMatchIn(Jsoup.parse(last.html).text().trim())
    }

    /** Footnote markers link here, followed by the note's HTML, URL-encoded. */
    private const val FOOTNOTE_SCHEME = "footnote:"

    /** The note's HTML if [url] is a footnote marker's link, else null. */
    fun footnote(url: String): String? =
        if (url.startsWith(FOOTNOTE_SCHEME)) URLDecoder.decode(url.removePrefix(FOOTNOTE_SCHEME), "UTF-8") else null

    fun parse(html: String, baseUrl: String): List<Block> {
        if (html.isBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html, baseUrl).body()
        linkFootnotes(body)
        return mutableListOf<Block>().also { walk(body, it, depth = 0) }
    }

    /**
     * In-page links have nowhere to go in a native view. Footnote markers (links to a list item
     * elsewhere in the post) instead carry their note, so tapping one can show it in place; the
     * notes' links back to their markers are dropped, and any other in-page link becomes text.
     */
    private fun linkFootnotes(body: Element) {
        // One pass to index the list items, rather than searching the document for each link.
        val items = body.select("li[id]").associateBy { it.id() }
        val links = body.select("a[href^=#]")
        val notes = links.mapNotNull { items[it.attr("href").drop(1)] }.toSet()
        notes.forEach { it.select("a[href^=#]").remove() }
        for (link in links) {
            if (link.parent() == null) continue // a back-link, removed above
            val note = items[link.attr("href").drop(1)]?.takeIf { it in notes }?.html()?.trim()
            if (note.isNullOrEmpty()) {
                link.unwrap()
            } else {
                link.attr("href", FOOTNOTE_SCHEME + URLEncoder.encode(note, "UTF-8"))
            }
        }
    }

    /** Every image in [blocks], including those in galleries and collapsed sections, in reading order. */
    fun images(blocks: List<Block>): List<Block.Image> = blocks.flatMap {
        when (it) {
            is Block.Image -> listOf(it)
            is Block.Gallery -> it.images
            is Block.Details -> images(it.blocks)
            else -> emptyList()
        }
    }

    private fun walk(parent: Element, out: MutableList<Block>, depth: Int) {
        val inline = StringBuilder()
        fun flush() {
            // Whitespace between blocks is the common case, and needs no parse to rule out.
            if (inline.isBlank()) {
                inline.clear()
                return
            }
            val text = inline.toString()
            if (Jsoup.parse(text).text().isNotBlank()) out += Block.Paragraph(text.trim())
            inline.clear()
        }
        for (node in parent.childNodes()) {
            when {
                node is TextNode -> inline.append(node.outerHtml())
                node !is Element -> Unit
                node.tagName() in SKIP -> Unit
                // Inline wrappers can hold a video (WordPress's <span class="embed-youtube">) or an
                // image (Blogger's <a href="full.jpg"><img></a>), which need blocks of their own.
                node.tagName() in INLINE && node.selectFirst("$MEDIA, $CONTENT_IMAGE") == null ->
                    inline.append(node.outerHtml())
                else -> {
                    flush()
                    block(node, out, depth)
                }
            }
        }
        flush()
    }

    private fun block(el: Element, out: MutableList<Block>, depth: Int) {
        if (el.classNames().any { it in GALLERIES }) return gallery(el, out)
        if (el.hasClass("wp-block-buttons") || el.hasClass("wp-block-button")) return buttons(el, out)
        when (el.tagName()) {
            "details" -> {
                val summary = el.children().firstOrNull { it.tagName() == "summary" }
                val title = summary?.text()?.ifEmpty { null } ?: "Details"
                summary?.remove()
                val inner = mutableListOf<Block>().also { walk(el, it, depth) }
                if (inner.isNotEmpty()) out += Block.Details(title, inner)
            }
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
            "table" -> table(el, caption = null)?.let { out += it }
            "figure" -> {
                val caption = el.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() }
                el.selectFirst("table")?.let { table ->
                    table(table, caption)?.let { out += it }
                    return
                }
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
                val cites = el.children().filter { it.tagName() == "cite" }
                val citation = cites.joinToString(" ") { it.text() }.takeIf { it.isNotBlank() }
                cites.forEach { it.remove() }
                val paragraphs = el.select("p")
                val html = if (paragraphs.isEmpty()) el.html() else paragraphs.joinToString("<br><br>") { it.html() }
                val pull = el.hasClass("wp-block-pullquote") || el.parent()?.hasClass("wp-block-pullquote") == true
                if (Jsoup.parse(html).text().isNotBlank()) out += Block.Quote(html.trim(), citation, pull)
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

    private fun gallery(el: Element, out: MutableList<Block>) {
        val images = el.select("img").mapNotNull { img ->
            val item = img.parents().takeWhile { it !== el }.firstOrNull { it.`is`(GALLERY_ITEM) }
            val caption = item?.selectFirst("figcaption, .gallery-caption, .tiled-gallery-caption")?.text()
            image(img, caption?.takeIf { it.isNotBlank() })
        }.distinctBy { it.url }
        val caption = el.selectFirst(".blocks-gallery-caption")?.text()?.takeIf { it.isNotBlank() }
        if (images.size < 2) {
            images.singleOrNull()?.let { out += it.copy(caption = it.caption ?: caption) }
            return
        }
        // Phones are too narrow for the wider layouts a site may ask for.
        val declared = el.classNames().firstNotNullOfOrNull { GALLERY_COLUMNS.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        val columns = minOf(declared ?: MAX_GALLERY_COLUMNS, MAX_GALLERY_COLUMNS, images.size).coerceAtLeast(1)
        out += Block.Gallery(images, columns, caption)
    }

    private fun buttons(el: Element, out: MutableList<Block>) {
        for (link in el.select("a[href]")) {
            val url = link.absUrl("href")
            val label = link.text()
            if (url.startsWith("http") && label.isNotEmpty()) out += Block.Button(label, url)
        }
    }

    private fun table(table: Element, caption: String?): Block.Table? {
        val rows = table.select("tr").map { row ->
            row.children().filter { it.tagName() in CELLS }.map { it.html().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        // Header rows are those in <thead>, or a first row made only of <th>.
        // Rows without cells were dropped above, so only count header rows that have them.
        val headerRows = (
            table.select("thead tr").count { row -> row.children().any { it.tagName() in CELLS } }
                .takeIf { it > 0 }
                ?: if (table.selectFirst("tr")?.children()?.all { it.tagName() == "th" } == true) 1 else 0
            ).coerceAtMost(rows.size)
        return Block.Table(rows, headerRows, caption)
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
        if (FeedParser.isTrackingImage(img, src)) return null
        // width/height describe the displayed (possibly cropped) size; when loading the original,
        // WordPress's data-orig-size ("w,h") gives that image's real shape.
        val size = img.attr("data-orig-size").split(',').mapNotNull { it.trim().toIntOrNull() }
            .takeIf { attr == "data-orig-file" && it.size == 2 }
            ?: listOf(img.attr("width"), img.attr("height")).map { it.trim().toIntOrNull() }
        val (width, height) = size.map { it?.takeIf { d -> d > 0 } }
        return Block.Image(src, caption, width, height)
    }
}
