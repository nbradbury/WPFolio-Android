package com.nbradbury.wpfolio.data.wpcom

import android.net.Uri
import androidx.core.net.toUri
import com.nbradbury.wpfolio.data.prefs.PendingOAuth
import com.nbradbury.wpfolio.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class WpcomToken(val accessToken: String, val expiresAt: Long?)

/**
 * WordPress.com OAuth2 authorization-code flow with PKCE, run through a Custom Tab. PKCE takes
 * the place of a client secret, so none ships in the app. (The implicit flow can't be used:
 * WordPress.com returns a blank page instead of redirecting to a custom scheme.)
 */
class WpcomAuth(
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    fun isRedirect(uri: Uri?): Boolean = uri != null && uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST

    suspend fun authorizeUri(): Uri {
        val pending = PendingOAuth(state = UUID.randomUUID().toString(), codeVerifier = newCodeVerifier())
        settings.setPendingOAuth(pending)
        return "https://public-api.wordpress.com/oauth2/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "global")
            .appendQueryParameter("state", pending.state)
            .appendQueryParameter("code_challenge", codeChallenge(pending.codeVerifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    /** Exchanges the code on the redirect for an access token. */
    suspend fun tokenFrom(redirect: Uri): WpcomToken {
        val pending = settings.pendingOAuth()
        settings.setPendingOAuth(null)
        val code = parseRedirect(redirect.encodedQuery, pending?.state)
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("redirect_uri", REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", pending!!.codeVerifier)
            .build()
        val request = Request.Builder().url("https://public-api.wordpress.com/oauth2/token").post(body).build()
        client.newCall(request).executeAsync().use { response ->
            val text = withContext(Dispatchers.IO) { response.body.string() }
            return parseTokenResponse(text, response.isSuccessful)
        }
    }

    companion object {
        /** Public client ID from https://developer.wordpress.com/apps/. */
        const val CLIENT_ID = "151832"

        const val REDIRECT_SCHEME = "wpfolio"
        const val REDIRECT_HOST = "oauth"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

        /** Returns the authorization code, or throws with WordPress.com's error or a state mismatch. */
        fun parseRedirect(query: String?, expectedState: String?): String {
            val params = parseParams(query)
            params["error"]?.let { throw IOException(params["error_description"] ?: it) }
            if (expectedState == null || expectedState != params["state"]) {
                throw IOException("Sign-in was interrupted; please try again")
            }
            return params["code"]?.takeIf { it.isNotBlank() }
                ?: throw IOException("WordPress.com didn't return a code")
        }

        fun parseTokenResponse(body: String, successful: Boolean, now: Long = System.currentTimeMillis()): WpcomToken {
            val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            if (!successful || json == null || "error" in json) {
                val message = json?.string("error_description") ?: json?.string("error")
                throw IOException(message?.let { "Sign-in failed: $it" } ?: "Sign-in failed")
            }
            val token = json.string("access_token")?.takeIf { it.isNotBlank() }
                ?: throw IOException("WordPress.com didn't return a token")
            val expiresAt = json["expires_in"]?.jsonPrimitive?.longOrNull?.let { now + it * 1000 }
            return WpcomToken(token, expiresAt)
        }

        /** RFC 7636: 32 random bytes, base64url without padding (43 characters). */
        fun newCodeVerifier(): String =
            ByteArray(32).also(SecureRandom()::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)

        fun codeChallenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

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
