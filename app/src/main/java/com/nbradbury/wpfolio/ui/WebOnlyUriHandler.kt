package com.nbradbury.wpfolio.ui

import androidx.compose.ui.platform.UriHandler

/**
 * Feed content decides which links the app opens, so only open web links. Other schemes
 * (intent:, file:, other apps' deep links) are ignored, as is a URL nothing can handle.
 */
class WebOnlyUriHandler(private val delegate: UriHandler) : UriHandler {
    override fun openUri(uri: String) {
        if (!isWebUrl(uri)) return
        runCatching { delegate.openUri(uri) }
    }

    companion object {
        fun isWebUrl(uri: String): Boolean =
            uri.trim().substringBefore(':', "").lowercase() in setOf("http", "https")
    }
}
