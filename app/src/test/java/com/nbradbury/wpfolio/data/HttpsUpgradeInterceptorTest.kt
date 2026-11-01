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
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        for (url in listOf("http://a.test/feed", "https://b.test/feed", "http://c.test:8080/x?y=1")) {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }

        assertEquals(listOf("https://a.test/feed", "https://b.test/feed", "https://c.test:8080/x?y=1"), sent)
    }
}
