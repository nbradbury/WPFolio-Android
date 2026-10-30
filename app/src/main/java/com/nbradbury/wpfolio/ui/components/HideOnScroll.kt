package com.nbradbury.wpfolio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Tracks scroll direction for a floating pill: hidden while content scrolls down, shown again
 * as soon as it scrolls up. Attach [connection] with `Modifier.nestedScroll` on a parent of
 * the scrolling content.
 */
@Stable
class HideOnScrollState {
    var visible by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        // Uses what the content actually scrolled, so dragging past either end doesn't toggle it.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            when {
                consumed.y < -THRESHOLD_PX -> visible = false
                consumed.y > THRESHOLD_PX -> visible = true
            }
            return Offset.Zero
        }
    }

    private companion object {
        const val THRESHOLD_PX = 1f
    }
}

@Composable
fun rememberHideOnScrollState() = remember { HideOnScrollState() }

/** Slides [content] off the bottom of the screen while [state] says it's hidden. */
@Composable
fun HideOnScroll(state: HideOnScrollState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
    ) {
        content()
    }
}
