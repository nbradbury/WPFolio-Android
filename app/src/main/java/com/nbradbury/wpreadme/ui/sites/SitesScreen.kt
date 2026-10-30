package com.nbradbury.wpreadme.ui.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpreadme.data.ReaderRepository
import com.nbradbury.wpreadme.data.db.SiteWithLatest
import com.nbradbury.wpreadme.ui.components.NavPill
import com.nbradbury.wpreadme.ui.components.SiteAvatar
import com.nbradbury.wpreadme.ui.components.Tab
import com.nbradbury.wpreadme.ui.components.accentFor
import com.nbradbury.wpreadme.ui.components.displayUrl
import com.nbradbury.wpreadme.ui.components.relativeTime
import com.nbradbury.wpreadme.ui.wpReadMeViewModel
import com.nbradbury.wpreadme.ui.theme.Destructive
import com.nbradbury.wpreadme.ui.theme.WpReadMe
import com.nbradbury.wpreadme.ui.theme.SnackbarAction
import com.nbradbury.wpreadme.ui.theme.SnackbarBackground
import com.nbradbury.wpreadme.ui.theme.SystemBarIcons
import com.nbradbury.wpreadme.ui.theme.ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SiteFilter(val label: String) { ALL("All"), WORDPRESS("WordPress"), RSS("RSS") }

data class SitesUiState(
    val sites: List<SiteWithLatest> = emptyList(),
    val total: Int = 0,
    val rssCount: Int = 0,
    val filter: SiteFilter = SiteFilter.ALL,
)

class SitesViewModel(
    private val repository: ReaderRepository,
    /** Outlives the screen so a swipe still commits if the user navigates away mid-snackbar. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val filter = MutableStateFlow(SiteFilter.ALL)

    /** Swiped away but not yet committed, so Undo can bring them back. */
    private val pending = MutableStateFlow<Set<String>>(emptySet())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val state = combine(repository.sitesWithLatest, filter, pending) { all, filter, pending ->
        val visible = all.filter { it.site.id !in pending }
        SitesUiState(
            sites = visible.filter {
                when (filter) {
                    SiteFilter.ALL -> true
                    SiteFilter.WORDPRESS -> it.site.isWordPress
                    SiteFilter.RSS -> !it.site.isWordPress
                }
            },
            total = visible.size,
            rssCount = visible.count { !it.site.isWordPress },
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitesUiState())

    fun setFilter(value: SiteFilter) = filter.update { value }

    /** Returns false if the site is already hidden, since a settled swipe can report its dismissal twice. */
    fun hide(siteId: String): Boolean {
        if (siteId in pending.value) return false
        pending.update { it + siteId }
        return true
    }

    fun undo(siteId: String) = pending.update { it - siteId }

    fun commit(siteId: String) {
        appScope.launch {
            try {
                repository.unsubscribe(siteId)
            } catch (e: Exception) {
                _messages.send("Couldn't unsubscribe. Please try again.")
            } finally {
                pending.update { it - siteId }
            }
        }
    }
}

@Composable
fun SitesScreen(onOpenSite: (String) -> Unit, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val viewModel = wpReadMeViewModel { SitesViewModel(it.repository, it.appScope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WpReadMe.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    SystemBarIcons(lightIcons = colors.isDark)

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val unsubscribe: (SiteWithLatest) -> Unit = { item ->
        if (viewModel.hide(item.site.id)) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            var undone = false
            try {
                undone = snackbar.showSnackbar(
                    message = "Unsubscribed from ${item.site.name}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                ) == SnackbarResult.ActionPerformed
            } finally {
                if (undone) viewModel.undo(item.site.id) else viewModel.commit(item.site.id)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "header") {
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Sites", style = ui(32f, 700, lineHeight = 1f, tracking = -0.03f), color = colors.text)
                        Text(
                            "${state.total} ${if (state.total == 1) "subscription" else "subscriptions"} · ${state.rssCount} via RSS",
                            style = ui(13f, 500),
                            color = colors.textSecondary,
                        )
                    }
                    Row(
                        Modifier.height(40.dp).clip(CircleShape).background(colors.inverse).clickable(onClick = onAdd)
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onInverse, modifier = Modifier.size(20.dp))
                        Text("Add", style = ui(14f, 600), color = colors.onInverse)
                    }
                }
            }
            item(key = "filters") {
                Row(
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SiteFilter.entries.forEach { filter ->
                        FilterChip(filter.label, selected = filter == state.filter) { viewModel.setFilter(filter) }
                    }
                }
            }
            items(state.sites, key = { it.site.id }) { item ->
                SwipeToUnsubscribe(item, onUnsubscribe = { unsubscribe(item) }) {
                    SiteRow(item) { onOpenSite(item.site.id) }
                }
            }
        }
        NavPill(Tab.SITES, onTab, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(14.dp),
                containerColor = SnackbarBackground,
                contentColor = Color(0xFFF2F2F0),
                actionColor = SnackbarAction,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WpReadMe.colors
    val base = Modifier.height(34.dp).clip(CircleShape)
    Box(
        (if (selected) base.background(colors.inverse) else base.border(1.dp, colors.outline, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ui(13f, if (selected) 600 else 500), color = if (selected) colors.onInverse else colors.text)
    }
}

@Composable
private fun SwipeToUnsubscribe(item: SiteWithLatest, onUnsubscribe: () -> Unit, content: @Composable () -> Unit) {
    // Deliberately not saveable: LazyColumn would restore a row brought back by Undo in its
    // dismissed position, which re-triggers onDismiss.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onUnsubscribe() },
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(Destructive), contentAlignment = Alignment.CenterEnd) {
                Column(
                    Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(Icons.Rounded.RemoveCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Unsubscribe", style = ui(11.5f, 600), color = Color.White)
                }
            }
        },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Unsubscribe from ${item.site.name}") { onUnsubscribe(); true })
        },
    ) {
        content()
    }
}

@Composable
private fun SiteRow(item: SiteWithLatest, onClick: () -> Unit) {
    val colors = WpReadMe.colors
    val site = item.site
    Row(
        Modifier.fillMaxWidth().background(colors.background).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(item.latestImage ?: site.iconUrl, site.name, accentFor(item.latestAccent, site.id), 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    site.name,
                    style = ui(15f, 600),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!site.isWordPress) RssBadge()
            }
            Text(
                displayUrl(if (site.isWordPress) site.siteUrl else site.feedUrl),
                style = ui(12.5f, 500),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.latestAt?.let { Text(relativeTime(it), style = ui(12f, 500), color = colors.textSecondary) }
    }
}

@Composable
fun RssBadge() {
    val colors = WpReadMe.colors
    Text(
        "RSS",
        style = ui(10f, 600, tracking = 0.05f),
        color = colors.textSecondary,
        modifier = Modifier
            .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
