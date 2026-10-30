package com.nbradbury.wpfolio.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.nbradbury.wpfolio.ui.theme.ui

data class ViewerImage(val url: String, val caption: String?)

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen viewer for a post's images: swipe between them, pinch or double-tap to zoom,
 * tap to show or hide the controls. Back or the close button dismisses it. The caller sets the
 * system bar icons, since it owns them for the rest of the screen.
 */
@Composable
fun PhotoViewer(images: List<ViewerImage>, startIndex: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) { images.size }
    var showControls by remember { mutableStateOf(true) }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { images[it].url }) { page ->
            ZoomableImage(images[page]) { showControls = !showControls }
        }
        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
                    if (images.size > 1) {
                        Text(
                            "${pager.currentPage + 1} / ${images.size}",
                            style = ui(14f, 600),
                            color = Color.White,
                            modifier = Modifier.weight(1f).padding(end = 40.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                images[pager.currentPage].caption?.let { caption ->
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
                            )
                            .navigationBarsPadding()
                            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)
                    ) {
                        Text(caption, style = ui(14f, 500, lineHeight = 1.4f), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(image: ViewerImage, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember { mutableStateOf(true) }

    // Keep the zoomed image covering the screen rather than panning off into black.
    fun clamp(value: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1) / 2
        val maxY = size.height * (atScale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                            // Zoom towards the tapped point.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Pinch always zooms; a one-finger drag only pans when already zoomed,
                        // otherwise it's left for the pager to swipe between images.
                        if (event.changes.size > 1 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = if (newScale == 1f) Offset.Zero else clamp(offset + event.calculatePan(), newScale)
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            // Load at full resolution so zooming in stays sharp.
            model = ImageRequest.Builder(LocalContext.current).data(image.url).size(Size.ORIGINAL).build(),
            contentDescription = image.caption,
            contentScale = ContentScale.Fit,
            onState = { loading = it is AsyncImagePainter.State.Loading },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        if (loading) CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
    }
}
