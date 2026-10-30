package com.nbradbury.wpreadme.data.rss

import com.nbradbury.wpreadme.data.SitePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException

sealed interface FetchResult {
    data object NotModified : FetchResult
    data class Updated(val feed: ParsedFeed, val etag: String?, val lastModified: String?) : FetchResult
}

class LookupException(message: String) : IOException(message)

interface RssSource {
    suspend fun fetch(feedUrl: String, etag: String?, lastModified: String?): FetchResult

    /** Resolves whatever the user typed (site, feed or page URL) to a feed and previews it. */
    suspend fun lookup(input: String): SitePreview
}

class OkHttpRssSource(private val client: OkHttpClient) : RssSource {

    override suspend fun fetch(feedUrl: String, etag: String?, lastModified: String?): FetchResult {
        val request = Request.Builder().url(feedUrl).apply {
            etag?.let { header("If-None-Match", it) }
            lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        client.newCall(request).executeAsync().use { response ->
            if (response.code == 304) return FetchResult.NotModified
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $feedUrl")
            val body = withContext(Dispatchers.IO) { response.body.string() }
            val feed = withContext(Dispatchers.Default) { FeedParser.parse(body, feedUrl) }
                ?: throw IOException("Not a feed: $feedUrl")
            return FetchResult.Updated(feed, response.header("ETag"), response.header("Last-Modified"))
        }
    }

    override suspend fun lookup(input: String): SitePreview {
        val url = normalizeUrl(input) ?: throw LookupException("That doesn't look like a web address")
        val (finalUrl, body) = get(url)

        if (FeedParser.looksLikeFeed(body)) {
            val feed = FeedParser.parse(body, finalUrl) ?: throw LookupException("Couldn't read that feed")
            return preview(feed, finalUrl, isWordPress = feed.isWordPress, fallbackTitle = "", fallbackDescription = "")
        }

        val page = withContext(Dispatchers.Default) { FeedDiscovery.parse(body, finalUrl) }
        val candidates = page.feedUrls.ifEmpty { listOf(originOf(finalUrl) + "/feed/") }
        for (candidate in candidates) {
            val feedBody = runCatching { get(candidate) }.getOrNull() ?: continue
            val feed = FeedParser.parse(feedBody.second, feedBody.first) ?: continue
            return preview(
                feed = feed,
                feedUrl = feedBody.first,
                isWordPress = page.isWordPress || feed.isWordPress,
                fallbackTitle = page.title,
                fallbackDescription = page.description,
                fallbackSiteUrl = finalUrl,
            )
        }
        throw LookupException("No RSS or Atom feed found at this address")
    }

    private fun preview(
        feed: ParsedFeed,
        feedUrl: String,
        isWordPress: Boolean,
        fallbackTitle: String,
        fallbackDescription: String,
        fallbackSiteUrl: String? = null,
    ): SitePreview {
        val siteUrl = feed.siteUrl ?: fallbackSiteUrl ?: originOf(feedUrl)
        return SitePreview(
            name = feed.title.ifBlank { fallbackTitle }.ifBlank { hostOf(siteUrl) },
            description = feed.description.ifBlank { fallbackDescription },
            siteUrl = siteUrl,
            feedUrl = feedUrl,
            isWordPress = isWordPress,
            items = feed.items,
        )
    }

    private suspend fun get(url: String): Pair<String, String> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) throw LookupException("The site returned an error (${response.code})")
            val body = withContext(Dispatchers.IO) { response.body.string() }
            return response.request.url.toString() to body
        }
    }

    companion object {
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || ' ' in trimmed || '.' !in trimmed) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return runCatching { java.net.URI(withScheme).takeIf { it.host != null }?.toString() }.getOrNull()
        }

        fun originOf(url: String): String =
            runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" } }.getOrDefault(url)

        fun hostOf(url: String): String =
            runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
