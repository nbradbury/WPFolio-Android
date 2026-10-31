package com.nbradbury.wpfolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nbradbury.wpfolio.data.AccentSampler
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.prefs.WpcomAccount
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.ui

/** The post's sampled accent, or a stable stand-in until sampling finishes. */
val PostEntity.accentColor: Color
    get() = Color(accent ?: AccentSampler.fallbackAccent(imageUrl ?: siteId))

fun accentFor(accent: Int?, key: String): Color = Color(accent ?: AccentSampler.fallbackAccent(key))

/** An image over its accent colour, so the layout reads as colour while it loads. */
@Composable
fun AccentImage(
    url: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    placeholder: String? = null,
) {
    val clipped = if (shape != null) modifier.clip(shape) else modifier
    Box(clipped.background(accent), contentAlignment = Alignment.Center) {
        if (url == null && placeholder != null) {
            Text(placeholder.take(1).uppercase(), style = ui(26f, 700), color = Color.White.copy(alpha = 0.85f))
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SiteAvatar(
    imageUrl: String?,
    name: String,
    accent: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    border: Pair<Dp, Color>? = null,
    initialSize: Float = size.value * 0.42f,
) {
    val bordered = if (border != null) modifier.border(border.first, border.second, CircleShape) else modifier
    Box(
        bordered.size(size).clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        // Fall back to the initial when there's no image or it fails to load.
        var failed by remember(imageUrl) { mutableStateOf(false) }
        if (imageUrl != null && !failed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(name.firstOrNull()?.uppercase().orEmpty(), style = ui(initialSize, 700), color = Color.White)
        }
    }
}

@Composable
fun AccountAvatar(account: WpcomAccount?, onClick: (() -> Unit)? = null, size: Dp = 32.dp, border: Color? = null) {
    val colors = WpFolio.colors
    // Only a button when there's something to do; otherwise it's just the account's picture.
    val base = Modifier.size(size).clip(CircleShape).let {
        if (onClick != null) it.clickable(role = Role.Button, onClickLabel = "Account", onClick = onClick) else it
    }
    Box(
        (if (border != null) base.border(2.dp, border, CircleShape) else base).background(colors.placeholderAvatar),
        contentAlignment = Alignment.Center,
    ) {
        if (account?.avatarUrl != null) {
            AsyncImage(account.avatarUrl, "Account", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Person, "Account", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.6f))
        }
    }
}

enum class Tab { HOME, SITES, ADD }

/** The single floating navigation pill. */
@Composable
fun NavPill(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    PillContainer(modifier) {
        PillButton(Icons.Rounded.Home, "Home", selected = current == Tab.HOME) { onSelect(Tab.HOME) }
        PillButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "Sites", selected = current == Tab.SITES) {
            onSelect(Tab.SITES)
        }
        PillButton(Icons.Rounded.Add, "Add a site", iconSize = 24.dp) { onSelect(Tab.ADD) }
    }
}

@Composable
fun PillContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .clip(CircleShape)
            .background(WpFolio.colors.pill)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
fun PillButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    val colors = WpFolio.colors
    val tint = if (selected) colors.pillSelectedIcon else colors.pillIcon
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) colors.pillSelected else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Round translucent button drawn over images (back, more). */
@Composable
fun OverlayButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** "now", "5m", "2h", "3d", "1w", then a short date. */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        minutes < 60 * 24 * 7 * 5 -> "${minutes / (60 * 24 * 7)}w"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(epochMillis))
    }
}

/** "4 min read" at about 230 words a minute, or null when there's no text to read. */
fun readTime(wordCount: Int): String? =
    if (wordCount > 0) "${((wordCount + 115) / 230).coerceAtLeast(1)} min read" else null

/** Host plus path, without scheme or trailing slash: "secondrun.film/rss". */
fun displayUrl(url: String): String =
    url.substringAfter("://").removePrefix("www.").trimEnd('/')
