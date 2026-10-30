package com.nbradbury.wpfolio.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpfolio.AppContainer
import com.nbradbury.wpfolio.data.LookupResult
import com.nbradbury.wpfolio.data.rss.LookupException
import com.nbradbury.wpfolio.data.rss.OkHttpRssSource
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.ui.components.AccentImage
import com.nbradbury.wpfolio.ui.components.SiteAvatar
import com.nbradbury.wpfolio.ui.components.accentFor
import com.nbradbury.wpfolio.ui.components.displayUrl
import com.nbradbury.wpfolio.ui.theme.Destructive
import com.nbradbury.wpfolio.ui.theme.SystemBarIcons
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.body
import com.nbradbury.wpfolio.ui.theme.ui
import com.nbradbury.wpfolio.ui.wpFolioViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface AddState {
    data object Idle : AddState
    data object Looking : AddState
    data class Found(val result: LookupResult, val accent: Int?) : AddState
    data class Results(val query: String, val results: List<SiteSearchResult>) : AddState
    data class Failed(val message: String) : AddState
}

/** Shorter queries are too vague to be worth a search. */
private const val MIN_SEARCH_LENGTH = 2

@OptIn(FlowPreview::class)
class AddViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state = _state.asStateFlow()

    /** Feed URL of the preview or search result being subscribed to, if any. */
    private val _subscribing = MutableStateFlow<String?>(null)
    val subscribing = _subscribing.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { lookup(it) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        if (value.isBlank()) _state.value = AddState.Idle
    }

    /** URLs are looked up directly; anything else searches WordPress.com Reader for sites. */
    private suspend fun lookup(input: String) {
        val trimmed = input.trim()
        when {
            OkHttpRssSource.normalizeUrl(trimmed) != null -> lookupUrl(trimmed)
            trimmed.length >= MIN_SEARCH_LENGTH -> search(trimmed)
        }
    }

    private suspend fun search(query: String) {
        _state.value = AddState.Looking
        _state.value = try {
            AddState.Results(query, container.repository.searchSites(query))
        } catch (e: Exception) {
            AddState.Failed("Couldn't search right now")
        }
    }

    private suspend fun lookupUrl(input: String) {
        _state.value = AddState.Looking
        _state.value = try {
            val result = container.repository.lookup(input)
            val accent = result.preview.images.firstOrNull()?.let { container.accentSampler.sample(it)?.color }
            AddState.Found(result, accent)
        } catch (e: LookupException) {
            AddState.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            AddState.Failed("Couldn't reach that address")
        }
    }

    fun subscribe(onDone: () -> Unit) {
        val preview = (_state.value as? AddState.Found)?.result?.preview ?: return
        subscribe(preview.feedUrl, onDone) { container.repository.subscribe(preview) }
    }

    fun subscribe(result: SiteSearchResult, onDone: () -> Unit) {
        subscribe(result.feedUrl, onDone) { container.repository.subscribe(result) }
    }

    private fun subscribe(key: String, onDone: () -> Unit, action: suspend () -> Unit) {
        if (_subscribing.value != null) return
        viewModelScope.launch {
            _subscribing.value = key
            try {
                action()
                onDone()
            } catch (e: Exception) {
                _state.value = AddState.Failed("Couldn't subscribe: ${e.message ?: "unknown error"}")
            } finally {
                _subscribing.value = null
            }
        }
    }
}

/**
 * 1g: paste any URL and the feed type is detected and previewed before subscribing, or type
 * anything else to search WordPress.com Reader for sites.
 */
@Composable
fun AddScreen(
    onClose: () -> Unit,
    onOpenSite: (SiteSearchResult) -> Unit,
    onSubscribed: () -> Unit,
) {
    val viewModel = wpFolioViewModel { AddViewModel(it) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribing by viewModel.subscribing.collectAsStateWithLifecycle()
    val colors = WpFolio.colors
    val focus = remember { FocusRequester() }
    SystemBarIcons(lightIcons = colors.isDark)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.padding(top = 4.dp).offset(x = (-10).dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Follow anything", style = ui(30f, 700, lineHeight = 1.05f, tracking = -0.03f), color = colors.text)
            Text(
                "Search for a site by name or topic, or paste the address of a WordPress site, " +
                    "an RSS or Atom feed, or any page that links to one.",
                style = body(14.5f, lineHeight = 1.45f),
                color = colors.bodyMuted,
            )
        }
        QueryField(
            value = query,
            onValueChange = viewModel::setQuery,
            loading = state is AddState.Looking,
            modifier = Modifier.focusRequester(focus),
        )
        when (val s = state) {
            is AddState.Failed -> Text(s.message, style = ui(13.5f, 500), color = Destructive)
            is AddState.Found -> {
                PreviewCard(
                    found = s,
                    subscribing = subscribing == s.result.preview.feedUrl,
                    onOpen = { onOpenSite(s.result.toSearchResult()) },
                    onSubscribe = { viewModel.subscribe(onSubscribed) },
                )
                if (!s.result.preview.isWordPress) {
                    Text(
                        "Not a WordPress site, so posts open in full-text mode when the feed allows it.",
                        style = ui(12.5f, 500, lineHeight = 1.4f),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    )
                }
            }
            is AddState.Results -> SearchResults(s, subscribing, onOpenSite) { viewModel.subscribe(it, onSubscribed) }
            else -> Unit
        }
    }
}

@Composable
private fun QueryField(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WpFolio.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.card)
            .border(2.dp, colors.text, shape)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (OkHttpRssSource.normalizeUrl(value) != null) Icons.Rounded.Link else Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search or paste a URL", style = ui(15.5f, 500), color = colors.textSecondary.copy(alpha = 0.6f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = ui(15.5f, 500).copy(color = colors.text),
                cursorBrush = SolidColor(colors.text),
                // Text rather than Uri so search queries can contain spaces.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onValueChange(value.trim()) }),
                modifier = modifier.fillMaxWidth(),
            )
        }
        when {
            loading -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.textSecondary, strokeWidth = 2.dp)
            value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Cancel, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PreviewCard(found: AddState.Found, subscribing: Boolean, onOpen: () -> Unit, onSubscribe: () -> Unit) {
    val colors = WpFolio.colors
    val preview = found.result.preview
    val accent = accentFor(found.accent, preview.feedUrl)
    val images = preview.images
    val shape = RoundedCornerShape(22.dp)
    val alreadySubscribed = found.result.subscribedSiteId != null
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(colors.card)
            .clickable(onClickLabel = "See posts", onClick = onOpen)
    ) {
        if (images.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(154.dp).background(accent),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AccentImage(images[0], accent, Modifier.weight(2f).fillMaxHeight())
                if (images.size > 1) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AccentImage(images[1], accent.copy(alpha = 0.85f), Modifier.weight(1f).fillMaxWidth())
                        AccentImage(images.getOrNull(2), accent.copy(alpha = 0.7f), Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val labelColor = if (colors.isDark) colors.textSecondary else accent
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (preview.isWordPress) Icons.Rounded.Public else Icons.Rounded.RssFeed,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (preview.isWordPress) "WORDPRESS SITE FOUND" else "RSS FEED FOUND",
                    style = ui(11.5f, 600, tracking = 0.06f),
                    color = labelColor,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(preview.name, style = ui(19f, 600), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (preview.description.isNotBlank()) {
                    Text(
                        preview.description,
                        style = body(14f, lineHeight = 1.4f),
                        color = colors.bodyMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val buttonModifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(48.dp).clip(CircleShape)
            if (alreadySubscribed) {
                Row(
                    buttonModifier.border(1.5.dp, colors.outline, CircleShape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Text(" Already subscribed", style = ui(15f, 600), color = colors.textSecondary)
                }
            } else {
                Box(
                    buttonModifier.background(colors.inverse).clickable(enabled = !subscribing, onClick = onSubscribe),
                    contentAlignment = Alignment.Center,
                ) {
                    if (subscribing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.onInverse, strokeWidth = 2.dp)
                    } else {
                        Text("Subscribe", style = ui(15f, 600), color = colors.onInverse)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: AddState.Results,
    subscribing: String?,
    onOpen: (SiteSearchResult) -> Unit,
    onSubscribe: (SiteSearchResult) -> Unit,
) {
    val colors = WpFolio.colors
    if (state.results.isEmpty()) {
        Text("No sites found for \u201c${state.query}\u201d", style = ui(14f, 500), color = colors.textSecondary)
        return
    }
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.results.forEach { result ->
            SearchResultRow(
                result = result,
                subscribing = subscribing == result.feedUrl,
                enabled = subscribing == null,
                onOpen = { onOpen(result) },
                onSubscribe = { onSubscribe(result) },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SiteSearchResult,
    subscribing: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val colors = WpFolio.colors
    Row(
        Modifier.clickable(onClickLabel = "See posts", onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(result.iconUrl, result.name, accentFor(null, result.feedUrl), 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.name, style = ui(15f, 600), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                displayUrl(result.siteUrl),
                style = ui(12.5f, 500),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.description.isNotBlank()) {
                Text(
                    result.description,
                    style = body(13f, lineHeight = 1.35f),
                    color = colors.bodyMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            result.subscribedSiteId != null -> Icon(
                Icons.Rounded.Check,
                contentDescription = "Subscribed",
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            subscribing -> CircularProgressIndicator(
                Modifier.size(22.dp),
                color = colors.textSecondary,
                strokeWidth = 2.dp,
            )
            else -> Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(colors.inverse)
                    .clickable(enabled = enabled, onClickLabel = "Subscribe to ${result.name}", onClick = onSubscribe)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Subscribe", style = ui(13f, 600), color = colors.onInverse)
            }
        }
    }
}

/** The URL-lookup preview as a search result, so it opens the same site preview. */
private fun LookupResult.toSearchResult() = SiteSearchResult(
    name = preview.name,
    siteUrl = preview.siteUrl,
    feedUrl = preview.feedUrl,
    description = preview.description,
    iconUrl = null,
    isWordPress = preview.isWordPress,
    subscribedSiteId = subscribedSiteId,
)
