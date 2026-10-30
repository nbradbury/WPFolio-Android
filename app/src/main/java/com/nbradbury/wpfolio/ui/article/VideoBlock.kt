package com.nbradbury.wpfolio.ui.article

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.htmlEncode
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.nbradbury.wpfolio.container
import com.nbradbury.wpfolio.ui.components.displayUrl
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.ui

/**
 * Sent as the referrer for embedded players. YouTube refuses to play in an app's WebView unless
 * the request identifies the app this way.
 */
private const val PLAYER_ORIGIN = "https://com.nbradbury.wpfolio"

/**
 * A video shown as its poster with a play button. Tapping swaps in the player, so nothing heavy
 * loads until the reader asks for it. Scrolling it away, or the post stopping being [active]
 * (the pager keeps neighbouring posts alive), releases the player and stops playback.
 */
@Composable
internal fun VideoBlock(video: Block.Video, accent: Color, active: Boolean) {
    var playing by remember(video) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) playing = false }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .aspectRatio(video.aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        if (playing) {
            VideoPlayer(video, Modifier.fillMaxSize())
        } else {
            VideoPoster(video, accent) { playing = true }
        }
    }
}

@Composable
private fun VideoPoster(video: Block.Video, accent: Color, onPlay: () -> Unit) {
    val repository = LocalContext.current.container.repository
    // VideoPress embeds don't include a poster, but WordPress.com's video API does.
    val poster by produceState(video.poster, video) {
        if (value == null && video.host == VideoHost.VIDEOPRESS) value = repository.videoPoster(video.id)
    }
    Box(Modifier.fillMaxSize().background(accent).clickable(onClickLabel = "Play video", onClick = onPlay)) {
        poster?.let {
            AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
            )
        )
        Box(
            Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Text(
            video.host.label,
            style = ui(12f, 600),
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoPlayer(video: Block.Video, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var webView by remember { mutableStateOf<WebView?>(null) }
    val chrome = remember { FullscreenChromeClient() }

    // Pause with the app, so audio doesn't carry on in the background.
    DisposableEffect(lifecycle, webView) {
        val view = webView ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Fill the box; a wrap-content WebView gives the page zero height, hiding the player.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webChromeClient = chrome
                webViewClient = PlayerClient(uriHandler)
                loadDataWithBaseURL(PLAYER_ORIGIN, playerHtml(video), "text/html", "utf-8", null)
                webView = this
            }
        },
        // Runs when the block scrolls away or the post is swiped past, which stops playback.
        onRelease = {
            chrome.exitFullscreen()
            it.destroy()
        },
        modifier = modifier,
    )
}

/** A page holding just the player, filling the WebView. */
private fun playerHtml(video: Block.Video): String {
    val player = when (video.host) {
        // IDs are restricted to URL-safe characters by VideoLinks, so they can go straight in.
        VideoHost.YOUTUBE -> iframe(
            "https://www.youtube.com/embed/${video.id}?autoplay=1&playsinline=1&rel=0&origin=$PLAYER_ORIGIN"
        )
        VideoHost.VIMEO -> iframe("https://player.vimeo.com/video/${video.id}?autoplay=1")
        VideoHost.VIDEOPRESS -> iframe("https://videopress.com/embed/${video.id}?autoPlay=1&controls=1&playsinline=1")
        VideoHost.FILE -> {
            val poster = video.poster?.let { " poster=\"${it.htmlEncode()}\"" }.orEmpty()
            "<video src=\"${video.id.htmlEncode()}\"$poster controls autoplay playsinline></video>"
        }
    }
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
        iframe,video{position:fixed;inset:0;border:0;width:100%;height:100%}</style></head>
        <body>$player</body></html>
    """.trimIndent()
}

private fun iframe(src: String) =
    "<iframe src=\"$src\" allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\" allowfullscreen></iframe>"

/** Keeps the player in place; links out of it (such as "Watch on YouTube") open in a tab instead. */
private class PlayerClient(private val uriHandler: UriHandler) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        uriHandler.openUri(request.url.toString())
        return true
    }
}

/** Shows the player's fullscreen view in a fullscreen dialog with the system bars hidden. */
private class FullscreenChromeClient : WebChromeClient() {
    private var dialog: Dialog? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        exitFullscreen()
        dialog = Dialog(view.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            setOnDismissListener {
                dialog = null
                callback.onCustomViewHidden()
            }
            show()
            window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    override fun onHideCustomView() = exitFullscreen()

    fun exitFullscreen() {
        dialog?.let {
            it.setOnDismissListener(null)
            dialog = null
            it.dismiss()
        }
    }
}

/** An iframe the app can't show natively, as a tappable card that opens it in a tab. */
@Composable
internal fun EmbedCard(embed: Block.Embed) {
    val colors = WpFolio.colors
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .border(1.dp, colors.outline, shape)
            .clickable(onClickLabel = "Open embedded content") { uriHandler.openUri(embed.url) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                embed.title ?: "Embedded content",
                style = ui(15f, 600),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                displayUrl(embed.url).substringBefore('/'),
                style = ui(12.5f, 500),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.textSecondary)
    }
}
