package com.nbradbury.wpfolio.ui.article

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nbradbury.wpfolio.ui.theme.WpFolioColors
import com.nbradbury.wpfolio.ui.theme.WpFolio
import uniffi.wp_mobile.ReaderAppColors
import uniffi.wp_mobile.ReaderFont
import uniffi.wp_mobile.ReaderPageOptions
import uniffi.wp_mobile.ReaderPostContent
import uniffi.wp_mobile.ReaderTheme
import uniffi.wp_mobile.ReaderThemeColors
import uniffi.wp_mobile.readerColorFromArgb
import uniffi.wp_mobile.readerPageBaseUrl

/**
 * Debug builds only: the post's body as wordpress-rs's shared Reader page, in a WebView as tall as
 * the page, so it scrolls with the native hero and header. [height] is in CSS pixels, which are dp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WpRsArticleBody(
    content: ReaderPostContent,
    height: Int,
    onHeight: (Int) -> Unit,
    onImageClick: (String) -> Unit,
    onAnchor: (y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WpFolio.colors
    val fontScale = LocalDensity.current.fontScale
    val page = remember(content, colors, fontScale) { content.pageHtml(pageOptions(colors, fontScale)) }
    val uriHandler by rememberUpdatedState(LocalUriHandler.current)
    val onMessage by rememberUpdatedState { message: String ->
        when (message.substringBefore(':')) {
            "height" -> message.substringAfter(':').toIntOrNull()?.let(onHeight)
            // image:<x>:<y>:<width>:<height>:<url>
            "image" -> message.split(':', limit = 6).getOrNull(5)?.let(onImageClick)
            // anchor:<y>:<id>
            "anchor" -> message.split(':', limit = 3).getOrNull(1)?.toIntOrNull()?.let(onAnchor)
            "blocked" -> Log.w(TAG, message)
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // The app draws the background behind the page.
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // The page scales its own text by the system's font scale.
                settings.textZoom = 100
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        uriHandler.openUri(request.url.toString())
                        return true
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail) = true
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "readerPost", setOf(PAGE_ORIGIN)) { _, msg, _, _, _ ->
                        msg.data?.let(onMessage)
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != page) {
                view.tag = page
                view.loadDataWithBaseURL(readerPageBaseUrl(), page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier.fillMaxWidth().height(height.coerceAtLeast(1).dp),
    )
}

/** The page in the app's own colours, with its article margins. */
private fun pageOptions(colors: WpFolioColors, fontScale: Float): ReaderPageOptions {
    fun Color.reader() = readerColorFromArgb(toArgb())
    // The app picks light or dark itself, so the page gets the same colours either way.
    val themeColors = ReaderThemeColors(
        text = colors.body.reader(),
        secondaryText = colors.textSecondary.reader(),
        tertiaryText = colors.bodyMuted.reader(),
        background = colors.articleBackground.reader(),
        secondaryBackground = colors.card.reader(),
        border = colors.divider.reader(),
        link = colors.text.reader(),
    )
    return ReaderPageOptions(
        theme = ReaderTheme.SYSTEM,
        appColors = ReaderAppColors(light = themeColors, dark = themeColors),
        language = null,
        offline = false,
        font = ReaderFont.SANS,
        textScale = fontScale.toDouble(),
        serverCss = null,
        css = "body { padding: 0 22px; }",
    )
}

private const val TAG = "WpRsArticleBody"

/** The origin of [readerPageBaseUrl], the only page allowed to message the app. */
private const val PAGE_ORIGIN = "https://wordpress.com"
