package com.nbradbury.wpfolio.data.wpcom

import android.net.Uri
import androidx.core.net.toUri
import com.nbradbury.wpfolio.data.prefs.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID

data class WpcomToken(val accessToken: String, val expiresAt: Long?)

/**
 * WordPress.com OAuth2 implicit flow, run through a Custom Tab. The token comes back in the
 * redirect's fragment, so no client secret is needed and none ships in the app.
 */
class WpcomAuth(private val settings: SettingsStore) {
    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank()

    fun isRedirect(uri: Uri?): Boolean = uri != null && uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST

    suspend fun authorizeUri(): Uri {
        val state = UUID.randomUUID().toString()
        settings.setOAuthState(state)
        return "https://public-api.wordpress.com/oauth2/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("scope", "global")
            .appendQueryParameter("state", state)
            .build()
    }

    suspend fun tokenFrom(redirect: Uri): WpcomToken {
        val expectedState = settings.oauthState()
        settings.setOAuthState(null)
        return parseRedirect(redirect.encodedFragment, redirect.encodedQuery, expectedState)
    }

    companion object {
        /**
         * Public client ID from https://developer.wordpress.com/apps/. Client IDs aren't secret,
         * so it lives in source; sign-in is disabled while it's blank.
         */
        const val CLIENT_ID = "151832"

        const val REDIRECT_SCHEME = "wpfolio"
        const val REDIRECT_HOST = "oauth"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

        /**
         * Reads the token from the redirect fragment. Errors can arrive in the fragment or the
         * query, depending on where the flow failed.
         */
        fun parseRedirect(
            fragment: String?,
            query: String?,
            expectedState: String?,
            now: Long = System.currentTimeMillis(),
        ): WpcomToken {
            val params = parseParams(query) + parseParams(fragment)
            params["error"]?.let { throw IOException(params["error_description"] ?: it) }
            if (expectedState == null || expectedState != params["state"]) {
                throw IOException("Sign-in was interrupted; please try again")
            }
            val token = params["access_token"]?.takeIf { it.isNotBlank() }
                ?: throw IOException("WordPress.com didn't return a token")
            val expiresAt = params["expires_in"]?.toLongOrNull()?.let { now + it * 1000 }
            return WpcomToken(token, expiresAt)
        }

        private fun parseParams(encoded: String?): Map<String, String> =
            encoded.orEmpty().split('&').filter { '=' in it }.associate {
                val (key, value) = it.split('=', limit = 2)
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
    }
}

/** Adds the stored bearer token to WordPress.com API calls. Runs on OkHttp's threads. */
class WpcomAuthInterceptor(private val settings: SettingsStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { settings.token() }
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
