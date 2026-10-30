package com.nbradbury.folio.data.rss

import org.jsoup.Jsoup

data class DiscoveredPage(
    val title: String,
    val description: String,
    val feedUrls: List<String>,
    val isWordPress: Boolean,
)

/** Finds feeds advertised by an HTML page and detects whether it's a WordPress site. */
object FeedDiscovery {
    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml", "application/feed+xml")

    fun parse(html: String, pageUrl: String): DiscoveredPage {
        val doc = Jsoup.parse(html, pageUrl)
        val feeds = doc.select("link[rel~=(?i)alternate][href]")
            .filter { it.attr("type").lowercase() in FEED_TYPES }
            // WordPress also advertises per-post comment feeds; the site feed comes first but skip them anyway.
            .filterNot { it.attr("href").contains("/comments/feed") }
            .map { it.absUrl("href") }
            .filter { it.isNotEmpty() }
            .distinct()
        val isWordPress = doc.selectFirst("link[rel=https://api.w.org/]") != null ||
            doc.select("meta[name=generator]").any { it.attr("content").contains("WordPress", ignoreCase = true) } ||
            html.contains("/wp-content/")
        return DiscoveredPage(
            title = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: doc.title(),
            description = doc.selectFirst("meta[name=description]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            feedUrls = feeds,
            isWordPress = isWordPress,
        )
    }
}
