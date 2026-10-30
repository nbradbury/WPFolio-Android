package com.nbradbury.folio.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbradbury.folio.container
import com.nbradbury.folio.ui.components.AccentImage
import com.nbradbury.folio.ui.theme.DarkColors
import com.nbradbury.folio.ui.theme.SystemBarIcons
import com.nbradbury.folio.ui.theme.body
import com.nbradbury.folio.ui.theme.ui

private data class Tile(val url: String, val accent: Color, val height: Dp)

private fun unsplash(id: String) = "https://images.unsplash.com/photo-$id?w=480&q=70&auto=format&fit=crop"

/** Placeholder photos from the design, shown before the user has any posts of their own. */
private val SAMPLE_COLUMNS = listOf(
    0.dp to listOf(
        Tile(unsplash("1506905925346-21bda4d32df4"), Color(0xFF3D4F6B), 170.dp),
        Tile(unsplash("1519681393784-d120267933ba"), Color(0xFF1F2A4A), 210.dp),
        Tile(unsplash("1495474472287-4d71bcdd2085"), Color(0xFF5B3A24), 160.dp),
    ),
    (-60).dp to listOf(
        Tile(unsplash("1485846234645-a62644f84728"), Color(0xFF5A2430), 200.dp),
        Tile(unsplash("1504674900247-0877df9cc836"), Color(0xFF7A3B1F), 160.dp),
        Tile(unsplash("1501785888041-af3ef285b470"), Color(0xFF2C5560), 220.dp),
    ),
    20.dp to listOf(
        Tile(unsplash("1490481651871-ab68de25d43d"), Color(0xFF6B4A52), 150.dp),
        Tile(unsplash("1470071459604-3b5ec3a7fe05"), Color(0xFF2F4A36), 220.dp),
        Tile(unsplash("1513694203232-719a280e022f"), Color(0xFF6B5A3E), 170.dp),
    ),
)

/** 1i: a drifting image mosaic, with two clear ways in. */
@Composable
fun OnboardingScreen(onSignIn: () -> Unit, onStartWithRss: () -> Unit, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val account by context.container.repository.account.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(account) { if (account != null) onSignedIn() }
    SystemBarIcons(lightIcons = true, lightNavIcons = true)

    val colors = DarkColors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Mosaic()
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .offset(y = 300.dp)
                .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background)))
        )
        Box(Modifier.fillMaxSize().padding(top = 519.dp).background(colors.background))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Folio", style = ui(18f, 700, tracking = -0.02f), color = colors.text.copy(alpha = 0.9f))
            Text(
                "Every blog you love, in full colour.",
                style = ui(34f, 600, lineHeight = 1.06f, tracking = -0.03f),
                color = colors.text,
            )
            Text(
                "Your WordPress.com subscriptions and any RSS feed, together in one calm place to read.",
                style = body(15.5f, lineHeight = 1.5f),
                color = Color(0xFFC4C4C0),
            )
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(colors.text).clickable(onClick = onSignIn),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Continue with WordPress.com", style = ui(15.5f, 600), color = Color(0xFF141414))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable(onClick = onStartWithRss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start with RSS feeds", style = ui(15.5f, 600), color = colors.text)
                }
            }
        }
    }
}

@Composable
private fun Mosaic() {
    val transition = rememberInfiniteTransition(label = "drift")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val density = LocalDensity.current
    // The rotated mosaic is wider than the screen so its corners never show.
    BoxWithConstraints {
        Row(
            Modifier
                .requiredWidth(maxWidth + 60.dp)
                .height(560.dp)
                .offset(y = (-40).dp)
                .rotate(-6f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SAMPLE_COLUMNS.forEachIndexed { index, (offset, tiles) ->
                val direction = if (index % 2 == 0) 1f else -1f
                Column(
                    Modifier
                        .weight(1f)
                        .offset(y = offset)
                        .graphicsLayer { translationY = with(density) { (drift * 48.dp.toPx()) * direction } },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Repeat the column so the drift never exposes its ends.
                    (tiles + tiles).forEach { tile ->
                        AccentImage(tile.url, tile.accent, Modifier.fillMaxWidth().height(tile.height), shape = RoundedCornerShape(16.dp))
                    }
                }
            }
        }
    }
}
