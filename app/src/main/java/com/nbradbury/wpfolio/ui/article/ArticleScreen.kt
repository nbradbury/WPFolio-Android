package com.nbradbury.wpfolio.ui.article

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nbradbury.wpfolio.container
import com.nbradbury.wpfolio.data.PostList
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.ui.components.AccentImage
import com.nbradbury.wpfolio.ui.components.HideOnScroll
import com.nbradbury.wpfolio.ui.components.OverlayButton
import com.nbradbury.wpfolio.ui.components.PhotoViewer
import com.nbradbury.wpfolio.ui.components.PillButton
import com.nbradbury.wpfolio.ui.components.PillContainer
import com.nbradbury.wpfolio.ui.components.SiteAvatar
import com.nbradbury.wpfolio.ui.components.ViewerImage
import com.nbradbury.wpfolio.ui.components.accentColor
import com.nbradbury.wpfolio.ui.components.rememberHideOnScrollState
import com.nbradbury.wpfolio.ui.openInBrowser
import com.nbradbury.wpfolio.ui.theme.SystemBarIcons
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.body
import com.nbradbury.wpfolio.ui.theme.ui
import com.nbradbury.wpfolio.ui.wpFolioViewModel
import kotlinx.coroutines.launch

private val LightHeroHeight = 330.dp
private val DarkHeroHeight = 300.dp
private val NoImageHeroHeight = 96.dp
private val SheetOverlap = 26.dp
private const val BODY_SIZE = 17f

@Composable
fun ArticleScreen(postId: String, list: PostList, onBack: () -> Unit, onOpenSite: (String) -> Unit) {
    val repository = LocalContext.current.container.repository
    // Captured once (and kept across rotation) so posts arriving in a refresh can't shift the pages.
    var ids by rememberSaveable(postId, list, stateSaver = PostIdsSaver) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(postId, list) {
        if (ids == null) ids = repository.postIds(list, postId).takeIf { postId in it } ?: listOf(postId)
    }
    val pages = ids ?: run {
        Box(Modifier.fillMaxSize().background(WpFolio.colors.articleBackground))
        return
    }
    val pager = rememberPagerState(initialPage = pages.indexOf(postId).coerceAtLeast(0)) { pages.size }
    var viewerOpen by remember { mutableStateOf(false) }
    // Only the post on screen is composed, so neighbours aren't marked read until swiped to.
    HorizontalPager(
        state = pager,
        key = { pages[it] },
        // The photo viewer pages through images, so it gets the swipes while it's open.
        userScrollEnabled = !viewerOpen,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        ArticlePage(
            postId = pages[page],
            isCurrent = page == pager.settledPage,
            onBack = onBack,
            onOpenSite = onOpenSite,
            onViewerOpenChange = { viewerOpen = it },
        )
    }
}

private val PostIdsSaver = Saver<List<String>?, ArrayList<String>>(
    save = { it?.let(::ArrayList) },
    restore = { it },
)

@Composable
private fun ArticlePage(
    postId: String,
    isCurrent: Boolean,
    onBack: () -> Unit,
    onOpenSite: (String) -> Unit,
    onViewerOpenChange: (Boolean) -> Unit,
) {
    val viewModel = wpFolioViewModel(key = postId) { ArticleViewModel(postId, it) }
    val post by viewModel.post.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val siteSubscribed by viewModel.siteSubscribed.collectAsStateWithLifecycle()
    val colors = WpFolio.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val current = post
    if (current == null) {
        Box(Modifier.fillMaxSize().background(colors.articleBackground))
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val heroHeight = when {
        // No featured image: a short accent band that just clears the top buttons.
        current.imageUrl == null -> with(density) { statusBarPx.toDp() } + NoImageHeroHeight
        colors.isDark -> DarkHeroHeight
        else -> LightHeroHeight
    }
    val heroPx = with(density) { heroHeight.roundToPx() }
    val overHero by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < heroPx - statusBarPx }
    }
    val viewerOpen = viewerIndex != null
    if (isCurrent) {
        SystemBarIcons(
            lightIcons = overHero || colors.isDark || viewerOpen,
            lightNavIcons = colors.isDark || viewerOpen,
        )
    }
    LaunchedEffect(viewerOpen, isCurrent) { if (isCurrent) onViewerOpenChange(viewerOpen) }

    // Featured image first, then the post's own images, in reading order.
    val images = remember(current.imageUrl, blocks) {
        (listOfNotNull(current.imageUrl?.let { ViewerImage(it, null) }) +
            blocks.orEmpty().filterIsInstance<Block.Image>().map { ViewerImage(it.url, it.caption) })
            .distinctBy { it.url }
    }
    val openImage: (String) -> Unit = { url -> viewerIndex = images.indexOfFirst { it.url == url }.coerceAtLeast(0) }

    val pill = rememberHideOnScrollState()
    Box(Modifier.fillMaxSize().background(colors.articleBackground).nestedScroll(pill.connection)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") { ArticleHero(current, heroHeight, onImageClick = openImage) }
            item(key = "header") {
                ArticleHeader(current, onOpenSite = if (siteSubscribed) ({ onOpenSite(current.siteId) }) else null)
            }
            val loaded = blocks
            if (loaded == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.textSecondary)
                    }
                }
            } else {
                items(loaded.size) { i -> BlockView(loaded[i], current.accentColor, isCurrent, onImageClick = openImage) }
                item(key = "footer") { ArticleFooter(current, summaryOnly = loaded.size < 3) }
            }
        }

        if (!overHero) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.articleBackground))
        }
        ProgressBar(listState, heroPx, statusBarPx, current.accentColor)
        TopButtons(current, onBack, onOpenSite.takeIf { siteSubscribed })

        HideOnScroll(pill, Modifier.align(Alignment.BottomCenter)) {
            PillContainer {
                PillButton(
                    if (current.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    if (current.isSaved) "Remove from saved" else "Save",
                    onClick = viewModel::toggleSaved,
                )
                PillButton(
                    if (current.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (current.isLiked) "Unlike" else "Like",
                    onClick = viewModel::toggleLiked,
                )
                PillButton(Icons.Rounded.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, current.title)
                        putExtra(Intent.EXTRA_TEXT, "${current.title}\n${current.url}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        viewerIndex?.let { index ->
            PhotoViewer(images, index, onDismiss = { viewerIndex = null })
        }
    }

}

@Composable
private fun ArticleHero(post: PostEntity, height: Dp, onImageClick: (String) -> Unit) {
    val colors = WpFolio.colors
    Box(Modifier.fillMaxWidth().height(height)) {
        val imageModifier = post.imageUrl?.let { url ->
            Modifier.clickable(onClickLabel = "View image") { onImageClick(url) }
        } ?: Modifier
        AccentImage(post.imageUrl, post.accentColor, Modifier.fillMaxSize().then(imageModifier))
        if (colors.isDark) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to colors.articleBackground,
                    )
                )
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.3f to Color.Transparent)
                )
            )
            // The text sheet's rounded top, overlapping the image.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(SheetOverlap)
                    .clip(RoundedCornerShape(topStart = SheetOverlap, topEnd = SheetOverlap))
                    .background(colors.articleBackground)
            )
        }
    }
}

@Composable
private fun ArticleHeader(post: PostEntity, onOpenSite: (() -> Unit)?) {
    val siteLink = if (onOpenSite != null) Modifier.clickable(onClick = onOpenSite) else Modifier
    val colors = WpFolio.colors
    Column(
        Modifier.padding(start = 22.dp, end = 22.dp, top = if (colors.isDark) 14.dp else 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (colors.isDark) {
            Text(
                post.siteName.uppercase(),
                style = ui(12f, 600, tracking = 0.06f),
                color = colors.bodyMuted,
                modifier = siteLink,
            )
        } else {
            Row(
                siteLink,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteAvatar(null, post.siteName, post.accentColor, 30.dp, initialSize = 13f)
                Column {
                    Text(post.siteName, style = ui(13.5f, 600), color = colors.text)
                    Text(byline(post), style = ui(12f, 500), color = colors.textSecondary)
                }
            }
        }
        Text(post.title, style = ui(27f, 600, lineHeight = 1.14f, tracking = -0.02f), color = colors.text)
    }
}

private fun byline(post: PostEntity): String {
    val minutes = (post.wordCount / 230).coerceAtLeast(1)
    return listOfNotNull(post.author, "$minutes min read").joinToString(" · ")
}

@Composable
private fun BlockView(block: Block, accent: Color, isCurrent: Boolean, onImageClick: (String) -> Unit) {
    val colors = WpFolio.colors
    val size = BODY_SIZE
    val links = TextLinkStyles(SpanStyle(color = colors.text, textDecoration = TextDecoration.Underline))
    val padded = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 7.dp)
    when (block) {
        is Block.Paragraph -> Text(
            AnnotatedString.fromHtml(block.html, linkStyles = links),
            style = body(size, lineHeight = 1.62f),
            color = colors.body,
            modifier = padded,
        )
        is Block.Heading -> Text(
            block.text,
            style = ui(if (block.level <= 2) size * 1.3f else size * 1.12f, 600, lineHeight = 1.25f, tracking = -0.01f),
            color = colors.text,
            modifier = padded.padding(top = 10.dp),
        )
        is Block.Image -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            // Reserve the image's height up front when the HTML says how big it is, so the text
            // below doesn't jump as images load.
            val ratio = block.aspectRatio
            val sized = if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.heightIn(min = 120.dp)
            AsyncImage(
                model = block.url,
                contentDescription = block.caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .then(sized)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.25f))
                    .clickable(onClickLabel = "View image") { onImageClick(block.url) },
            )
            block.caption?.let {
                Text(it, style = ui(13f, 500), color = colors.textSecondary, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp))
            }
        }
        is Block.Quote -> Row(padded.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(accent))
            Text(
                AnnotatedString.fromHtml(block.html, linkStyles = links),
                style = body(size * 1.06f, lineHeight = 1.55f).copy(fontStyle = FontStyle.Italic),
                color = colors.body,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        is Block.ListItem -> Row(padded.padding(start = (block.depth * 18).dp)) {
            Text(block.marker, style = body(size, lineHeight = 1.62f), color = colors.textSecondary, modifier = Modifier.width(24.dp))
            Text(AnnotatedString.fromHtml(block.html, linkStyles = links), style = body(size, lineHeight = 1.62f), color = colors.body)
        }
        is Block.Code -> Text(
            block.text,
            style = ui(14f).copy(fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
            color = colors.body,
            modifier = padded
                .clip(RoundedCornerShape(12.dp))
                .background(colors.divider)
                .padding(14.dp),
        )
        is Block.Video -> VideoBlock(block, accent, active = isCurrent)
        is Block.Embed -> EmbedCard(block)
        Block.Rule -> HorizontalDivider(padded.padding(vertical = 12.dp), color = colors.divider)
    }
}

@Composable
private fun ArticleFooter(post: PostEntity, summaryOnly: Boolean) {
    val colors = WpFolio.colors
    val uriHandler = LocalUriHandler.current
    if (post.url.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (summaryOnly) {
            Text(
                "This feed only shares a summary.",
                style = body(14.5f),
                color = colors.bodyMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Box(
            Modifier
                .clip(CircleShape)
                .border(1.5.dp, colors.text, CircleShape)
                .clickable { uriHandler.openUri(post.url) }
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(if (summaryOnly) "Continue reading on ${post.siteName}" else "Open original", style = ui(14f, 600), color = colors.text)
        }
    }
}

/** The accent progress bar starts at the top of the text sheet and sticks under the status bar. */
@Composable
private fun ProgressBar(listState: LazyListState, heroPx: Int, statusBarPx: Int, accent: Color) {
    val colors = WpFolio.colors
    val density = LocalDensity.current
    val overlapPx = with(density) { if (colors.isDark) 0 else SheetOverlap.roundToPx() }
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            when {
                total == 0 -> 0f
                !listState.canScrollForward -> 1f
                else -> {
                    val first = info.visibleItemsInfo.firstOrNull()
                    val fraction = if (first != null && first.size > 0) -first.offset.toFloat() / first.size else 0f
                    ((listState.firstVisibleItemIndex + fraction) / total).coerceIn(0f, 1f)
                }
            }
        }
    }
    val y by remember {
        derivedStateOf {
            val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else Int.MAX_VALUE / 2
            (heroPx - overlapPx - scrolled).coerceAtLeast(statusBarPx)
        }
    }
    Box(
        Modifier
            .offsetY { y }
            .padding(horizontal = 26.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(colors.text.copy(alpha = 0.08f))
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(accent))
    }
}

private fun Modifier.offsetY(y: () -> Int) = this.then(Modifier.offset { IntOffset(0, y()) })

@Composable
private fun TopButtons(post: PostEntity, onBack: () -> Unit, onOpenSite: ((String) -> Unit)?) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Box {
            OverlayButton(Icons.Rounded.MoreVert, "More") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                    menu = false
                    context.openInBrowser(post.url)
                })
                DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                    menu = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(post.title, post.url))) }
                })
                if (onOpenSite != null) {
                    DropdownMenuItem(text = { Text("View ${post.siteName}") }, onClick = {
                        menu = false
                        onOpenSite(post.siteId)
                    })
                }
            }
        }
    }
}
