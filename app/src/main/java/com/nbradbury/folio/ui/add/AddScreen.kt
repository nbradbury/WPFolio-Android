package com.nbradbury.folio.ui.add

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.folio.AppContainer
import com.nbradbury.folio.data.LookupResult
import com.nbradbury.folio.data.rss.LookupException
import com.nbradbury.folio.data.rss.OkHttpRssSource
import com.nbradbury.folio.ui.components.AccentImage
import com.nbradbury.folio.ui.components.accentFor
import com.nbradbury.folio.ui.folioViewModel
import com.nbradbury.folio.ui.theme.Destructive
import com.nbradbury.folio.ui.theme.Folio
import com.nbradbury.folio.ui.theme.SystemBarIcons
import com.nbradbury.folio.ui.theme.body
import com.nbradbury.folio.ui.theme.ui
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
    data class Failed(val message: String) : AddState
}

@OptIn(FlowPreview::class)
class AddViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state = _state.asStateFlow()

    private val _subscribing = MutableStateFlow(false)
    val subscribing = _subscribing.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { lookup(it) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        if (OkHttpRssSource.normalizeUrl(value) == null) _state.value = AddState.Idle
    }

    private suspend fun lookup(input: String) {
        if (OkHttpRssSource.normalizeUrl(input) == null) return
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
        val found = _state.value as? AddState.Found ?: return
        if (_subscribing.value) return
        viewModelScope.launch {
            _subscribing.value = true
            try {
                container.repository.subscribe(found.result.preview)
                onDone()
            } catch (e: Exception) {
                _state.value = AddState.Failed("Couldn't subscribe: ${e.message ?: "unknown error"}")
            } finally {
                _subscribing.value = false
            }
        }
    }
}

/** 1g: paste any URL; the feed type is detected and previewed before subscribing. */
@Composable
fun AddScreen(onClose: () -> Unit, onSubscribed: () -> Unit) {
    val viewModel = folioViewModel { AddViewModel(it) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribing by viewModel.subscribing.collectAsStateWithLifecycle()
    val colors = Folio.colors
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
                "Paste the address of a WordPress site, an RSS or Atom feed, or any page that links to one.",
                style = body(14.5f, lineHeight = 1.45f),
                color = colors.bodyMuted,
            )
        }
        UrlField(
            value = query,
            onValueChange = viewModel::setQuery,
            loading = state is AddState.Looking,
            modifier = Modifier.focusRequester(focus),
        )
        when (val s = state) {
            is AddState.Failed -> Text(s.message, style = ui(13.5f, 500), color = Destructive)
            is AddState.Found -> {
                PreviewCard(s, subscribing) { viewModel.subscribe(onSubscribed) }
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
            else -> Unit
        }
    }
}

@Composable
private fun UrlField(value: String, onValueChange: (String) -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    val colors = Folio.colors
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
        Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("example.com", style = ui(15.5f, 500), color = colors.textSecondary.copy(alpha = 0.6f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = ui(15.5f, 500).copy(color = colors.text),
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onValueChange(value.trim()) }),
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
private fun PreviewCard(found: AddState.Found, subscribing: Boolean, onSubscribe: () -> Unit) {
    val colors = Folio.colors
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
