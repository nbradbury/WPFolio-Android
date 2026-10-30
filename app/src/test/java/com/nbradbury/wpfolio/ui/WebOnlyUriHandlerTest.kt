package com.nbradbury.wpfolio.ui

import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class WebOnlyUriHandlerTest {
    private val opened = mutableListOf<String>()
    private val handler = WebOnlyUriHandler(object : UriHandler {
        override fun openUri(uri: String) {
            if ("crash" in uri) throw IllegalArgumentException("no handler")
            opened += uri
        }
    })

    @Test
    fun `opens only web links`() {
        listOf(
            "https://example.com/post",
            "HTTP://example.com",
            "intent://scan/#Intent;scheme=zxing;end",
            "file:///data/data/com.nbradbury.wpfolio/files/datastore/settings.preferences_pb",
            "otherapp://deep/link",
            "javascript:alert(1)",
            "/relative/path",
        ).forEach(handler::openUri)
        assertEquals(listOf("https://example.com/post", "HTTP://example.com"), opened)
    }

    @Test
    fun `swallows failures to open a web link`() {
        handler.openUri("https://crash.example.com")
        assertEquals(emptyList<String>(), opened)
    }
}
