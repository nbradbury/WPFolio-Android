package com.nbradbury.wpfolio.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cleartext traffic is blocked, yet feeds, search results, discovered links and images often
 * still use http:// URLs that work over https, so every request goes out over https.
 */
class HttpsUpgradeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.isHttps) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("https").build()).build())
    }
}
