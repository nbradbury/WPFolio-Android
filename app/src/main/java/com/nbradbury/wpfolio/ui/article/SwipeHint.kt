package com.nbradbury.wpfolio.ui.article

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val VISIBLE_MS = 4_000L
private val StartShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
private val EndShape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)

/**
 * Small tabs on the screen edges pointing at the neighbouring posts, nudging a couple of times
 * before fading. Shown for a few seconds each time a post opens; the caller stops showing it
 * once the reader has swiped.
 */
@Composable
fun SwipeHint(hasPrevious: Boolean, hasNext: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(600) // let the post settle first
        visible = true
        nudge.animateTo(1f, repeatable(4, tween(450), RepeatMode.Reverse))
        nudge.snapTo(0f)
        delay(VISIBLE_MS - 1_800)
        visible = false
    }
    Box(modifier.fillMaxSize()) {
        if (hasPrevious) {
            EdgeTab(visible, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, Alignment.CenterStart) {
                IntOffset((-nudge.value * 6.dp.roundToPx()).toInt(), 0)
            }
        }
        if (hasNext) {
            EdgeTab(visible, Icons.AutoMirrored.Rounded.KeyboardArrowRight, Alignment.CenterEnd) {
                IntOffset((nudge.value * 6.dp.roundToPx()).toInt(), 0)
            }
        }
    }
}

@Composable
private fun BoxScope.EdgeTab(
    visible: Boolean,
    icon: ImageVector,
    alignment: Alignment,
    offset: Density.() -> IntOffset,
) {
    val start = alignment == Alignment.CenterStart
    AnimatedVisibility(visible, Modifier.align(alignment), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .offset(offset)
                .size(width = 22.dp, height = 56.dp)
                .background(Color.Black.copy(alpha = 0.28f), if (start) StartShape else EndShape),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the gesture it points at is the accessible action.
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
