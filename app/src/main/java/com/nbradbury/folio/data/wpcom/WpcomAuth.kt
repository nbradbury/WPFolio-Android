package com.nbradbury.folio.data.wpcom

import android.net.Uri
import androidx.core.net.toUri
import com.nbradbury.folio.BuildConfig
import com.nbradbury.folio.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.UUID

/** WordPress.com OAuth2 authorization-code flow, run through a Custom Tab. */
class WpcomAuth(
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    val isConfigured: Boolean get() = BuildConfig.WPCOM_CLIENT_ID.isNotBlank()

    fun isRedirect(uri: Uri?): Boolean {
        val redirect = BuildConfig.WPCOM_REDIRECT_URI.toUri()
        return uri != null && uri.scheme == redirect.scheme && uri.host == redirect.host
    }

    suspend fun authorizeUri(): Uri {
        val state = UUID.randomUUID().toString()
        settings.setOAuthState(state)
        return "https://public-api.wordpress.com/oauth2/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", BuildConfig.WPCOM_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.WPCOM_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "global")
            .appendQueryParameter("state", state)
            .build()
    }

    /** Exchanges the code on the redirect URI for an access token. */
    suspend fun exchange(redirect: Uri): String {
        redirect.getQueryParameter("error")?.let { throw IOException(redirect.getQueryParameter("error_description") ?: it) }
        val code = redirect.getQueryParameter("code") ?: throw IOException("WordPress.com didn't return a code")
        val expected = settings.oauthState()
        settings.setOAuthState(null)
        if (expected == null || expected != redirect.getQueryParameter("state")) {
            throw IOException("Sign-in was interrupted; please try again")
        }
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.WPCOM_CLIENT_ID)
            .add("client_secret", BuildConfig.WPCOM_CLIENT_SECRET)
            .add("redirect_uri", BuildConfig.WPCOM_REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .build()
        val request = Request.Builder().url("https://public-api.wordpress.com/oauth2/token").post(body).build()
        client.newCall(request).executeAsync().use { response ->
            val text = withContext(Dispatchers.IO) { response.body.string() }
            if (!response.isSuccessful) throw IOException("Sign-in failed (${response.code})")
            return Json.parseToJsonElement(text).jsonObject["access_token"]?.jsonPrimitive?.content
                ?: throw IOException("WordPress.com didn't return a token")
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
