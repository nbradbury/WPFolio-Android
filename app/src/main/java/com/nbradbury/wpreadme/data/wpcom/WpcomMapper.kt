package com.nbradbury.wpreadme.data.wpcom

import com.nbradbury.wpreadme.data.db.Origin
import com.nbradbury.wpreadme.data.db.PostEntity
import com.nbradbury.wpreadme.data.db.SiteEntity
import com.nbradbury.wpreadme.data.rss.FeedParser
import com.nbradbury.wpreadme.data.rss.OkHttpRssSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

data class WpcomPost(val post: PostEntity, val liked: Boolean)

data class WpcomMe(val displayName: String, val avatarUrl: String?)

/**
 * Maps WordPress.com Reader JSON to entities. The API is inconsistent about whether IDs are
 * numbers or strings, so everything is read leniently.
 */
object WpcomMapper {

    fun siteKey(feedId: Long?, blogId: Long?): String =
        if (feedId != null && feedId > 0) "wp:$feedId" else "wp:blog:${blogId ?: 0}"

    fun me(json: JsonObject) = WpcomMe(
        displayName = json.string("display_name") ?: json.string("username").orEmpty(),
        avatarUrl = json.string("avatar_URL"),
    )

    fun subscriptions(json: JsonObject): List<SiteEntity> =
        json.array("subscriptions").mapNotNull { (it as? JsonObject)?.let(::subscription) }

    fun subscription(json: JsonObject): SiteEntity? {
        val url = json.string("URL") ?: return null
        val feedId = json.long("feed_ID")?.takeIf { it > 0 }
        val blogId = json.long("blog_ID")?.takeIf { it > 0 }
        val data = json.obj("meta")?.obj("data")
        val site = data?.obj("site")
        val feed = data?.obj("feed")
        return SiteEntity(
            id = siteKey(feedId, blogId),
            origin = Origin.WPCOM,
            name = (site?.string("name") ?: feed?.string("name"))?.unescape()?.takeIf { it.isNotBlank() }
                ?: OkHttpRssSource.hostOf(url),
            siteUrl = url,
            feedUrl = feed?.string("feed_URL") ?: url,
            description = site?.string("description")?.unescape().orEmpty(),
            iconUrl = site?.obj("icon")?.string("img"),
            isWordPress = blogId != null,
            wpcomFeedId = feedId,
            wpcomBlogId = blogId,
        )
    }

    fun nextPageHandle(json: JsonObject): String? = json.string("next_page_handle")

    fun posts(json: JsonObject, siteIdOverride: String? = null): List<WpcomPost> =
        json.array("posts").mapNotNull { (it as? JsonObject)?.let { post -> post(post, siteIdOverride) } }

    fun post(json: JsonObject, siteIdOverride: String? = null): WpcomPost? {
        val postId = json.long("ID") ?: return null
        val blogId = json.long("site_ID")?.takeIf { it > 0 }
        val feedId = json.long("feed_ID")?.takeIf { it > 0 }
        val isExternal = json.bool("is_external") == true || blogId == null
        val content = json.string("content").orEmpty()
        val url = json.string("URL").orEmpty()
        val image = listOf(
            json.string("featured_image"),
            json.obj("featured_media")?.takeIf { it.string("type") == "image" }?.string("uri"),
            json.obj("post_thumbnail")?.string("URL"),
        ).firstOrNull { !it.isNullOrBlank() } ?: FeedParser.firstContentImage(content, url)
        val entity = PostEntity(
            id = if (isExternal) "wpf:$feedId:${json.long("feed_item_ID") ?: postId}" else "wps:$blogId:$postId",
            siteId = siteIdOverride ?: siteKey(feedId, blogId),
            siteName = (json.string("site_name")
                ?: json.obj("meta")?.obj("data")?.obj("site")?.string("name"))?.unescape().orEmpty(),
            title = json.string("title")?.unescape().orEmpty(),
            excerpt = Jsoup.parse(json.string("excerpt").orEmpty()).text(),
            contentHtml = content,
            url = url,
            author = json.obj("author")?.string("name")?.takeIf { it.isNotBlank() },
            imageUrl = image,
            publishedAt = FeedParser.parseDate(json.string("date")) ?: System.currentTimeMillis(),
            wordCount = json.long("word_count")?.toInt() ?: 0,
            wpcomBlogId = if (isExternal) null else blogId,
            wpcomPostId = if (isExternal) null else postId,
        )
        return WpcomPost(entity, liked = json.bool("i_like") == true)
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
/** Only real JSON strings; WP.com sends `false` for some missing string fields. */
private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content
private fun JsonObject.long(key: String): Long? = primitive(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.let { it.booleanOrNull ?: (it.longOrNull?.let { n -> n != 0L }) }
private fun String.unescape(): String = Parser.unescapeEntities(this, false)
