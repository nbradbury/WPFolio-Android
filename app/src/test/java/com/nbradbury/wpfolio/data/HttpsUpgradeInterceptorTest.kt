package com.nbradbury.wpfolio.data

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpsUpgradeInterceptorTest {

    @Test
    fun `sends http requests over https and leaves https alone`() {
        val sent = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpsUpgradeInterceptor())
            // Stands in for the network: records the request and answers it.
            .addInterceptor { chain ->
                sent += chain.request().url.toString()
                response(chain.request(), 200)
            }
            .build()

        for (url in listOf("http://a.test/feed", "https://b.test/feed", "http://c.test:8080/x?y=1")) {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }

        assertEquals(listOf("https://a.test/feed", "https://b.test/feed", "https://c.test:8080/x?y=1"), sent)
    }

    @Test
    fun `upgrades redirects to http`() {
        // As a network interceptor it sees each redirect before OkHttp follows it.
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpsUpgradeInterceptor())
            .addInterceptor { chain -> response(chain.request(), 301).newBuilder().header("Location", "HTTP://www.a.test/feed").build() }
            .build()

        client.newCall(Request.Builder().url("https://a.test/feed").build()).execute().use {
            assertEquals("https://www.a.test/feed", it.header("Location"))
        }
    }

    private fun response(request: Request, code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body("".toResponseBody())
        .build()
}
