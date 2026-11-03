package com.nbradbury.wpfolio.data.comments

import com.nbradbury.wpfolio.data.prefs.SettingsStore
import com.nbradbury.wpfolio.data.rss.stringWithLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import rs.wordpress.api.kotlin.WpApiClient
import rs.wordpress.api.kotlin.WpRequestExecutor
import rs.wordpress.api.kotlin.WpRequestResult
import uniffi.wp_api.ApiUrlResolver
import uniffi.wp_api.CommentCreateParams
import uniffi.wp_api.CommentListParams
import uniffi.wp_api.CommentStatus
import uniffi.wp_api.CommentWithViewContext
import uniffi.wp_api.ParsedUrl
import uniffi.wp_api.UserAvatarSize
import uniffi.wp_api.WpApiParamCommentsOrderBy
import uniffi.wp_api.WpApiParamOrder
import uniffi.wp_api.WpAuthentication
import uniffi.wp_api.WpAuthenticationProvider
import uniffi.wp_api.WpComBaseUrl
import uniffi.wp_api.WpComDotOrgApiUrlResolver
import uniffi.wp_api.WpOrgSiteApiUrlResolver
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Comments through wordpress-rs: WordPress.com sites via its `wp/v2` proxy (with the user's token
 * when signed in), self-hosted sites via their own REST API without authentication.
 */
class WordPressRsCommentsSource(
    private val settings: SettingsStore,
    private val http: OkHttpClient,
    interceptors: List<Interceptor>,
) : CommentsSource {
    private val executor = WpRequestExecutor(interceptors, networkAvailabilityProvider = { true })
    private val apiRoots = ConcurrentHashMap<String, String>()

    override suspend fun list(thread: CommentThread, page: Int): CommentPage {
        val params = CommentListParams(
            post = listOf(thread.postId),
            page = page.toUInt(),
            perPage = PAGE_SIZE.toUInt(),
            order = WpApiParamOrder.ASC,
            orderby = WpApiParamCommentsOrderBy.DATE_GMT,
        )
        val response = client(thread).request { it.comments().listWithViewContext(params) }.orThrow()
        return CommentPage(response.data.map(::toComment), hasMore = response.nextPageParams != null)
    }

    override suspend fun create(thread: CommentThread, text: String, parentId: Long?): Comment {
        val params = CommentCreateParams(post = thread.postId, parent = parentId, content = text)
        return toComment(client(thread).request { it.comments().create(params) }.orThrow().data)
    }

    override suspend fun resolve(thread: CommentThread): CommentThread = when (thread) {
        is CommentThread.WordPressCom -> thread
        is CommentThread.SelfHosted ->
            wpcomSiteOf(apiRoot(thread.siteUrl))?.let { CommentThread.WordPressCom(it, thread.postId) } ?: thread
    }

    private suspend fun client(thread: CommentThread): WpApiClient {
        val resolved = resolve(thread)
        val resolver: ApiUrlResolver = when (resolved) {
            is CommentThread.WordPressCom -> WpComDotOrgApiUrlResolver(resolved.site, WpComBaseUrl.Production)
            is CommentThread.SelfHosted -> WpOrgSiteApiUrlResolver(ParsedUrl.parse(apiRoot(resolved.siteUrl)))
        }
        val token = if (resolved is CommentThread.WordPressCom) settings.token() else null
        val auth = WpAuthenticationProvider.staticWithAuth(
            token?.let { WpAuthentication.Bearer(it) } ?: WpAuthentication.None,
        )
        return WpApiClient(resolver, auth, executor)
    }

    /**
     * The REST API root the site advertises, falling back to the usual `/wp-json/`. Sites hosted on
     * WordPress.com point at its public API. A network failure throws, so it isn't remembered.
     */
    private suspend fun apiRoot(siteUrl: String): String = apiRoots.getOrPut(siteUrl) {
        try {
            advertisedApiRoot(siteUrl)
        } catch (e: IOException) {
            throw CommentsException("Couldn't reach the site")
        } ?: (siteUrl.trimEnd('/') + "/wp-json/")
    }

    // On IO throughout: closing a response whose body wasn't read resets the stream, which writes.
    private suspend fun advertisedApiRoot(siteUrl: String): String? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(siteUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.headers("Link").firstNotNullOfOrNull { apiRootFromLink(it) }
                ?: Jsoup.parse(response.body.stringWithLimit(), response.request.url.toString())
                    .selectFirst("link[rel=https://api.w.org/][href]")?.absUrl("href")?.ifEmpty { null }
        }
    }

    private fun toComment(comment: CommentWithViewContext) = Comment(
        id = comment.id,
        parentId = comment.parent,
        author = comment.authorName.ifBlank { "Anonymous" },
        avatarUrl = comment.authorAvatarUrls?.let { it[UserAvatarSize.Size96] ?: it[UserAvatarSize.Size48] },
        html = comment.content.rendered,
        postedAt = comment.dateGmt?.time,
        held = comment.status == CommentStatus.Hold,
    )

    private fun <T> WpRequestResult<T>.orThrow(): T = when (this) {
        is WpRequestResult.Success -> response
        // The site's own message, such as "Sorry, comments are closed for this item."
        is WpRequestResult.WpError -> throw CommentsException(errorMessage)
        else -> throw CommentsException("Couldn't reach the site")
    }

    companion object {
        private const val PAGE_SIZE = 100
        private val LINK_API_ROOT = Regex("""<([^>]+)>\s*;\s*rel="https://api\.w\.org/"""")
        private val WPCOM_SITE = Regex("""^https://public-api\.wordpress\.com/.*[?&]rest_route=/sites/([^/&]+)""")

        /** The API root from a `Link` header such as `<https://example.com/wp-json/>; rel="https://api.w.org/"`. */
        fun apiRootFromLink(header: String): String? = LINK_API_ROOT.find(header)?.groupValues?.get(1)

        /** The WordPress.com site an API root on its public API belongs to, such as `en.blog.wordpress.com`. */
        fun wpcomSiteOf(apiRoot: String): String? = WPCOM_SITE.find(apiRoot)?.groupValues?.get(1)
    }
}
