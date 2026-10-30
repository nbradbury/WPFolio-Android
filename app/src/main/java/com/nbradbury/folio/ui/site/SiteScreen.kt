package com.nbradbury.folio.ui.site

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.folio.data.ReaderRepository
import com.nbradbury.folio.data.db.PostEntity
import com.nbradbury.folio.data.db.SiteEntity
import com.nbradbury.folio.ui.components.AccentImage
import com.nbradbury.folio.ui.components.OverlayButton
import com.nbradbury.folio.ui.components.SiteAvatar
import com.nbradbury.folio.ui.components.accentColor
import com.nbradbury.folio.ui.components.accentFor
import com.nbradbury.folio.ui.components.displayUrl
import com.nbradbury.folio.ui.folioViewModel
import com.nbradbury.folio.ui.theme.Folio
import com.nbradbury.folio.ui.theme.SystemBarIcons
import com.nbradbury.folio.ui.theme.body
import com.nbradbury.folio.ui.theme.ui
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SiteViewModel(private val siteId: String, private val repository: ReaderRepository) : ViewModel() {
    val site = repository.site(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val posts = repository.sitePosts(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { repository.refreshSite(siteId) } }
    }

    fun unsubscribe(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.unsubscribe(siteId) }.onSuccess { onDone() }
        }
    }
}

private val CoverHeight = 220.dp
private val AvatarSize = 76.dp

/** 1h: a mosaic cover of recent images, with posts in a grid below. */
@Composable
fun SiteScreen(siteId: String, onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    val viewModel = folioViewModel(key = siteId) { SiteViewModel(siteId, it.repository) }
    val site by viewModel.site.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val colors = Folio.colors
    val gridState = rememberLazyGridState()
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val coverPx = with(density) { CoverHeight.roundToPx() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val overCover by remember {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < coverPx - statusBarPx }
    }
    SystemBarIcons(lightIcons = overCover || colors.isDark)

    val current = site ?: run {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }
    val accent = posts.firstOrNull { it.imageUrl != null }?.accentColor ?: accentFor(null, current.id)
    val covers = posts.mapNotNull { p -> p.imageUrl?.let { it to p.accentColor } }.take(3)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "cover", span = { GridItemSpan(maxLineSpan) }) { Cover(covers, accent) }
            item(key = "info", span = { GridItemSpan(maxLineSpan) }) {
                SiteInfo(current, accent, onUnsubscribe = { confirmUnsubscribe = true })
            }
            itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                val edge = if (index % 2 == 0) Modifier.padding(start = 18.dp) else Modifier.padding(end = 18.dp)
                GridTile(post, edge) { onOpenPost(post.id) }
            }
        }
        if (!overCover) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.background))
        }
        OverlayButton(
            Icons.AutoMirrored.Rounded.ArrowBack,
            "Back",
            Modifier.statusBarsPadding().padding(start = 14.dp, top = 8.dp),
            onClick = onBack,
        )
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text("Unsubscribe from ${current.name}?") },
            text = { Text("Its posts will be removed from your feed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    viewModel.unsubscribe(onBack)
                }) { Text("Unsubscribe") }
            },
            dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text("Cancel") } },
            containerColor = colors.sheet,
        )
    }
}

@Composable
private fun Cover(images: List<Pair<String, Color>>, accent: Color) {
    Box(Modifier.fillMaxWidth().height(CoverHeight).background(accent)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AccentImage(images.getOrNull(0)?.first, images.getOrNull(0)?.second ?: accent, Modifier.weight(1.4f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AccentImage(images.getOrNull(1)?.first, images.getOrNull(1)?.second ?: accent, Modifier.weight(1f).fillMaxWidth())
                AccentImage(images.getOrNull(2)?.first, images.getOrNull(2)?.second ?: accent, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.4f to Color.Transparent)))
    }
}

@Composable
private fun SiteInfo(site: SiteEntity, accent: Color, onUnsubscribe: () -> Unit) {
    val colors = Folio.colors
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SiteAvatar(
            imageUrl = null,
            name = site.name,
            accent = accent,
            size = AvatarSize,
            border = 4.dp to colors.background,
            initialSize = 30f,
            // Half the avatar overlaps the cover; the extra 12dp cancels the grid spacing.
            modifier = Modifier.overlapAbove(AvatarSize / 2 + 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(site.name, style = ui(25f, 700, tracking = -0.02f), color = colors.text)
            Text(
                "${displayUrl(site.siteUrl)} · ${if (site.isWordPress) "WordPress" else "RSS"}",
                style = ui(13f, 500),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (site.description.isNotBlank()) {
            Text(site.description, style = body(15f, lineHeight = 1.5f), color = colors.body)
        }
        Row(Modifier.padding(top = 2.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(CircleShape).border(1.5.dp, colors.text, CircleShape).clickable(onClick = onUnsubscribe),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
                Text("Subscribed", style = ui(14f, 600), color = colors.text)
            }
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, colors.outline, CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = colors.text, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open site") }, onClick = {
                        menu = false
                        uriHandler.openUri(site.siteUrl)
                    })
                    DropdownMenuItem(text = { Text("Copy feed URL") }, onClick = {
                        menu = false
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(site.name, site.feedUrl))) }
                    })
                }
            }
        }
    }
}

@Composable
private fun GridTile(post: PostEntity, modifier: Modifier, onClick: () -> Unit) {
    val colors = Folio.colors
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AccentImage(post.imageUrl, post.accentColor, Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(14.dp), placeholder = post.siteName)
        Text(post.title, style = ui(14f, 600, lineHeight = 1.25f), color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Pulls this element up over the previous one by [amount] without leaving a gap below. */
private fun Modifier.overlapAbove(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}
