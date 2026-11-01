package com.nbradbury.wpfolio.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cleartext traffic is blocked, yet feeds, search results, discovered links and images often
 * still use http:// URLs that work over https. Added as an application interceptor it upgrades
 * each request; added as a network interceptor it upgrades redirects to http://, which OkHttp
 * otherwise follows after the application interceptors have run.
 */
class HttpsUpgradeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = if (request.url.isHttps) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("https").build()).build())
        }
        val location = response.header("Location")
        if (!response.isRedirect || location == null || !location.startsWith("http://", ignoreCase = true)) return response
        return response.newBuilder().header("Location", upgrade(location)).build()
    }

    companion object {
        /** [url] with an http:// scheme replaced by https://, as requests to it are sent. */
        fun upgrade(url: String): String =
            if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else url
    }
}
