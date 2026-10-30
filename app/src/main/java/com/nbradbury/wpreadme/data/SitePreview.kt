package com.nbradbury.wpreadme.data

import com.nbradbury.wpreadme.data.rss.ParsedItem

/** What the Add screen shows before subscribing. */
data class SitePreview(
    val name: String,
    val description: String,
    val siteUrl: String,
    val feedUrl: String,
    val isWordPress: Boolean,
    val items: List<ParsedItem>,
) {
    val images: List<String> get() = items.mapNotNull { it.imageUrl }.distinct().take(3)
}
