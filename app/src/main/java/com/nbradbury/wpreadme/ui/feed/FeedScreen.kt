package com.nbradbury.wpreadme.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbradbury.wpreadme.R
import com.nbradbury.wpreadme.data.db.PostEntity
import com.nbradbury.wpreadme.data.prefs.WpcomAccount
import com.nbradbury.wpreadme.ui.account.AccountSheet
import com.nbradbury.wpreadme.ui.components.AccentImage
import com.nbradbury.wpreadme.ui.components.AccountAvatar
import com.nbradbury.wpreadme.ui.components.NavPill
import com.nbradbury.wpreadme.ui.components.SiteAvatar
import com.nbradbury.wpreadme.ui.components.Tab
import com.nbradbury.wpreadme.ui.components.accentColor
import com.nbradbury.wpreadme.ui.components.relativeTime
import com.nbradbury.wpreadme.ui.wpReadMeViewModel
import com.nbradbury.wpreadme.ui.theme.WpReadMe
import com.nbradbury.wpreadme.ui.theme.SystemBarIcons
import com.nbradbury.wpreadme.ui.theme.body
import com.nbradbury.wpreadme.ui.theme.ui

private val HeroHeight = 480.dp
private val PillClearance = 120.dp

@Composable
fun FeedScreen(
    onOpenPost: (String) -> Unit,
    onTab: (Tab) -> Unit,
    onSignIn: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    val viewModel = wpReadMeViewModel { FeedViewModel(it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    val colors = WpReadMe.colors

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.posts.isEmpty() -> EmptyFeed(loading = !state.loaded || refreshing, onAdd = { onTab(Tab.ADD) })
                colors.isDark -> DarkFeed(state, onOpenPost, { showAccount = true }, viewModel::loadMore)
                else -> MagazineFeed(state, onOpenPost, { showAccount = true }, viewModel::loadMore)
            }
        }
        NavPill(Tab.HOME, onTab, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (showAccount) {
        AccountSheet(onDismiss = { showAccount = false }, onSignIn = onSignIn, onOpenSaved = {
            showAccount = false
            onOpenSaved()
        })
    }
}

/** 1a: full-bleed hero, then two-column masonry tiles. */
@Composable
private fun MagazineFeed(
    state: FeedUiState,
    onOpenPost: (String) -> Unit,
    onAccount: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = WpReadMe.colors
    val hero = state.posts.firstOrNull { it.imageUrl != null } ?: state.posts.first()
    val tiles = remember(state.posts, hero) { state.posts.filter { it.id != hero.id } }
    val gridState = rememberLazyStaggeredGridState()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val heroPx = with(density) { HeroHeight.roundToPx() }
    val overHero by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < heroPx - statusBarPx
        }
    }
    SystemBarIcons(lightIcons = overHero)
    LoadMoreEffect({ gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }, tiles.size, onLoadMore)

    Box {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = PillClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 18.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero", span = StaggeredGridItemSpan.FullLine) {
                Hero(hero, state.account, onAccount, { onOpenPost(hero.id) }, Modifier.bleed(14.dp))
            }
            itemsIndexed(tiles, key = { _, post -> post.id }) { index, post ->
                Tile(post, index) { onOpenPost(post.id) }
            }
        }
        if (!overHero) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.background))
        }
    }
}

@Composable
private fun Hero(
    post: PostEntity,
    account: WpcomAccount?,
    onAccount: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(HeroHeight).clickable(onClick = onClick)) {
        AccentImage(post.imageUrl, post.accentColor, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.22f to Color.Transparent,
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                )
            )
        )
        Row(
            Modifier.statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = ui(20f, 700, tracking = -0.02f), color = Color.White)
            AccountAvatar(account, onAccount, border = Color.White.copy(alpha = 0.8f))
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteAvatar(
                    imageUrl = null,
                    name = post.siteName,
                    accent = post.accentColor,
                    size = 20.dp,
                    border = 1.5.dp to Color.White.copy(alpha = 0.7f),
                    initialSize = 10f,
                )
                Text(post.siteName, style = ui(12.5f, 500), color = Color.White, maxLines = 1)
                Text("· ${relativeTime(post.publishedAt)}", style = ui(12.5f, 500), color = Color.White.copy(alpha = 0.7f))
            }
            Text(
                post.title,
                style = ui(27f, 600, lineHeight = 1.12f, tracking = -0.02f),
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.excerpt.isNotBlank()) {
                Text(
                    post.excerpt,
                    style = body(14.5f, lineHeight = 1.45f),
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Tile(post: PostEntity, index: Int, onClick: () -> Unit) {
    val colors = WpReadMe.colors
    Column(
        Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (post.imageUrl != null) {
            // Masonry: portrait-ish images stay 4:5, everything else squares off.
            val ratio = (post.imageRatio ?: if (index % 4 == 0 || index % 4 == 3) 0.8f else 1f).coerceIn(0.8f, 1f)
            AccentImage(
                post.imageUrl,
                post.accentColor,
                Modifier.fillMaxWidth().aspectRatio(ratio),
                shape = RoundedCornerShape(16.dp),
            )
        }
        Text(
            post.title,
            style = ui(14.5f, 600, lineHeight = 1.25f),
            color = colors.text,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.imageUrl == null && post.excerpt.isNotBlank()) {
            Text(post.excerpt, style = body(13f, 1.4f), color = colors.bodyMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${post.siteName} · ${relativeTime(post.publishedAt)}",
            style = ui(12f, 500),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 1c: a portrait rail of the newest posts, then a quiet list. */
@Composable
private fun DarkFeed(
    state: FeedUiState,
    onOpenPost: (String) -> Unit,
    onAccount: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = WpReadMe.colors
    val rail = remember(state.posts) { state.posts.filter { it.imageUrl != null }.take(4) }
    val latest = remember(state.posts, rail) { state.posts - rail.toSet() }
    val listState = rememberLazyListState()
    SystemBarIcons(lightIcons = true)
    LoadMoreEffect({ listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }, latest.size + 3, onLoadMore)

    Box {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = PillClearance), modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.app_name), style = ui(20f, 700, tracking = -0.02f), color = colors.text)
                    AccountAvatar(state.account, onAccount, size = 30.dp)
                }
            }
            if (rail.isNotEmpty()) {
                item(key = "rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rail, key = { it.id }) { post -> RailCard(post) { onOpenPost(post.id) } }
                    }
                }
            }
            item(key = "latest-header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("Latest", style = ui(16f, 600), color = colors.text)
                    if (state.unread > 0) Text("${state.unread} unread", style = ui(12.5f, 500), color = colors.textSecondary)
                }
            }
            items(latest, key = { it.id }) { post -> ListRow(post) { onOpenPost(post.id) } }
        }
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.background))
    }
}

@Composable
private fun RailCard(post: PostEntity, onClick: () -> Unit) {
    Box(
        Modifier.width(228.dp).height(320.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        AccentImage(post.imageUrl, post.accentColor, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(post.siteName, style = ui(12f, 500), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(
                post.title,
                style = ui(18f, 600, lineHeight = 1.18f, tracking = -0.01f),
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ListRow(post: PostEntity, onClick: () -> Unit) {
    val colors = WpReadMe.colors
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentImage(post.imageUrl, post.accentColor, Modifier.size(76.dp), shape = RoundedCornerShape(14.dp), placeholder = post.siteName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    post.title,
                    style = ui(15f, 600, lineHeight = 1.25f),
                    color = if (post.isRead) colors.textSecondary else colors.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${post.siteName} · ${relativeTime(post.publishedAt)}",
                    style = ui(12f, 500),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun EmptyFeed(loading: Boolean, onAdd: () -> Unit) {
    val colors = WpReadMe.colors
    SystemBarIcons(lightIcons = colors.isDark)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(color = colors.text)
        } else {
            Text("Nothing to read yet", style = ui(22f, 600, tracking = -0.02f), color = colors.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Follow a WordPress site or any RSS feed to fill this page.",
                style = body(15f),
                color = colors.bodyMuted,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.clip(CircleShape).background(colors.inverse).clickable(onClick = onAdd)
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            ) {
                Text("Add a site", style = ui(15f, 600), color = colors.onInverse)
            }
        }
    }
}

@Composable
private fun LoadMoreEffect(lastVisibleIndex: () -> Int, count: Int, onLoadMore: () -> Unit) {
    LaunchedEffect(count) {
        snapshotFlow { lastVisibleIndex() }.collect { if (count > 0 && it >= count - 6) onLoadMore() }
    }
}

/** Lets a full-span grid item ignore the grid's horizontal content padding. */
private fun Modifier.bleed(horizontal: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
