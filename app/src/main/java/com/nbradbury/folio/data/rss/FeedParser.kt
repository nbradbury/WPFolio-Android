package com.nbradbury.folio.data.rss

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val description: String,
    val generator: String?,
    val items: List<ParsedItem>,
) {
    val isWordPress: Boolean get() = generator?.contains("wordpress", ignoreCase = true) == true
}

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val author: String?,
    val publishedAt: Long?,
    val summaryHtml: String,
    val contentHtml: String,
    val imageUrl: String?,
)

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom documents. */
object FeedParser {

    /** Returns null when [xml] isn't a recognisable feed. */
    fun parse(xml: String, feedUrl: String): ParsedFeed? {
        val doc = runCatching { Jsoup.parse(xml, feedUrl, Parser.xmlParser()) }.getOrNull() ?: return null
        val root = doc.children().firstOrNull { it.tagName().lowercase() in ROOT_TAGS } ?: return null
        return when (root.tagName().lowercase()) {
            "feed" -> parseAtom(root, feedUrl)
            else -> parseRss(root, feedUrl)
        }
    }

    fun looksLikeFeed(body: String): Boolean {
        val head = body.take(2048).lowercase()
        return "<rss" in head || "<feed" in head || "<rdf:rdf" in head
    }

    private fun parseRss(root: Element, feedUrl: String): ParsedFeed {
        val channel = root.child("channel") ?: root
        val siteUrl = channel.child("link")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: channel.child("atom:link")?.attr("href")
        // RSS 1.0 puts items beside the channel rather than inside it.
        val itemElements = channel.childrenNamed("item").ifEmpty { root.childrenNamed("item") }
        val items = itemElements.map { item ->
            val link = item.child("link")?.text()?.trim().orEmpty()
            val base = link.ifEmpty { siteUrl ?: feedUrl }
            val summary = item.child("description")?.htmlText().orEmpty()
            val content = item.child("content:encoded")?.htmlText().orEmpty()
            ParsedItem(
                guid = item.child("guid")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: link,
                title = item.child("title")?.text()?.decodeEntities().orEmpty(),
                link = link,
                author = (item.child("dc:creator") ?: item.child("author"))?.text()?.trim()?.takeIf { it.isNotEmpty() },
                publishedAt = parseDate(
                    (item.child("pubDate") ?: item.child("dc:date") ?: item.child("published"))?.text()
                ),
                summaryHtml = summary,
                contentHtml = content,
                imageUrl = findImage(item, content.ifEmpty { summary }, base),
            )
        }
        return ParsedFeed(
            title = channel.child("title")?.text()?.decodeEntities().orEmpty(),
            siteUrl = siteUrl,
            description = channel.child("description")?.text()?.decodeEntities().orEmpty(),
            generator = channel.child("generator")?.text(),
            items = items,
        )
    }

    private fun parseAtom(root: Element, feedUrl: String): ParsedFeed {
        val siteUrl = root.alternateLink()
        val items = root.childrenNamed("entry").map { entry ->
            val link = entry.alternateLink().orEmpty()
            val base = link.ifEmpty { siteUrl ?: feedUrl }
            val summary = entry.child("summary")?.atomHtml().orEmpty()
            val content = entry.child("content")?.atomHtml().orEmpty()
            ParsedItem(
                guid = entry.child("id")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: link,
                title = entry.child("title")?.atomHtml()?.let { Jsoup.parse(it).text() }.orEmpty(),
                link = link,
                author = entry.child("author")?.child("name")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                publishedAt = parseDate((entry.child("published") ?: entry.child("updated"))?.text()),
                summaryHtml = summary,
                contentHtml = content,
                imageUrl = findImage(entry, content.ifEmpty { summary }, base),
            )
        }
        return ParsedFeed(
            title = root.child("title")?.text()?.decodeEntities().orEmpty(),
            siteUrl = siteUrl,
            description = root.child("subtitle")?.text()?.decodeEntities().orEmpty(),
            generator = root.child("generator")?.text(),
            items = items,
        )
    }

    private fun findImage(item: Element, html: String, baseUrl: String): String? {
        val mediaContents = item.childrenNamed("media:content") +
            item.childrenNamed("media:group").flatMap { it.childrenNamed("media:content") }
        val media = mediaContents.firstOrNull { el ->
            el.attr("medium") == "image" || el.attr("type").startsWith("image/") ||
                (el.attr("medium").isEmpty() && el.attr("type").isEmpty() && IMAGE_EXT.containsMatchIn(el.attr("url")))
        }?.attr("url")
        val thumbnail = (item.child("media:thumbnail")
            ?: item.child("media:group")?.child("media:thumbnail"))?.attr("url")
        val enclosure = item.childrenNamed("enclosure").firstOrNull { it.attr("type").startsWith("image/") }?.attr("url")
        val fromHtml = if (html.isBlank()) null else firstContentImage(html, baseUrl)
        return listOf(media, thumbnail, enclosure, fromHtml)
            .firstOrNull { !it.isNullOrBlank() }
            ?.let { resolve(baseUrl, it) }
    }

    fun firstContentImage(html: String, baseUrl: String): String? =
        Jsoup.parse(html, baseUrl).select("img[src]").firstOrNull { img ->
            img.attr("width") != "1" && img.attr("height") != "1" &&
                TRACKERS.none { it in img.attr("src") }
        }?.absUrl("src")?.takeIf { it.isNotEmpty() }

    private fun resolve(base: String, url: String): String =
        runCatching { java.net.URI(base).resolve(url.trim()).toString() }.getOrDefault(url)

    fun parseDate(raw: String?): Long? {
        val value = raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        for (formatter in RFC822_FORMATS) {
            runCatching { return ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli() }
        }
        return null
    }

    private val ROOT_TAGS = setOf("rss", "feed", "rdf:rdf")
    private val IMAGE_EXT = Regex("\\.(jpe?g|png|webp|gif|avif)(\\?|$)", RegexOption.IGNORE_CASE)
    private val TRACKERS = listOf("feeds.feedburner.com", "pixel.wp.com", "stats.wordpress.com", "gravatar.com/avatar")
    private val RFC822_FORMATS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
    )
}

private fun Element.child(name: String): Element? =
    children().firstOrNull { it.tagName().equals(name, ignoreCase = true) }

private fun Element.childrenNamed(name: String): List<Element> =
    children().filter { it.tagName().equals(name, ignoreCase = true) }

/** Text of an element whose content is escaped or CDATA-wrapped HTML. */
private fun Element.htmlText(): String = wholeText().trim()

private fun Element.atomHtml(): String =
    if (attr("type") == "xhtml") html().trim() else wholeText().trim()

private fun Element.alternateLink(): String? =
    children().filter { it.tagName() == "link" }
        .firstOrNull { it.attr("rel").isEmpty() || it.attr("rel") == "alternate" }
        ?.attr("href")?.takeIf { it.isNotEmpty() }

private fun String.decodeEntities(): String = Parser.unescapeEntities(trim(), false)
