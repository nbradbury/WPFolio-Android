package com.nbradbury.wpfolio.data.comments

import com.nbradbury.wpfolio.data.prefs.SettingsStore
import okhttp3.Interceptor
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

/**
 * Comments through wordpress-rs: WordPress.com sites via its `wp/v2` proxy (with the user's token
 * when signed in), self-hosted sites via their own REST API without authentication.
 */
class WordPressRsCommentsSource(
    private val settings: SettingsStore,
    interceptors: List<Interceptor>,
) : CommentsSource {
    private val executor = WpRequestExecutor(interceptors, networkAvailabilityProvider = { true })

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

    private suspend fun client(thread: CommentThread): WpApiClient {
        val resolver: ApiUrlResolver = when (thread) {
            is CommentThread.WordPressCom ->
                WpComDotOrgApiUrlResolver(thread.blogId.toString(), WpComBaseUrl.Production)
            is CommentThread.SelfHosted -> WpOrgSiteApiUrlResolver(ParsedUrl.parse(thread.apiRoot))
        }
        val token = if (thread is CommentThread.WordPressCom) settings.token() else null
        val auth = WpAuthenticationProvider.staticWithAuth(
            token?.let { WpAuthentication.Bearer(it) } ?: WpAuthentication.None,
        )
        return WpApiClient(resolver, auth, executor)
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

    private companion object {
        const val PAGE_SIZE = 100
    }
}
