package com.nbradbury.wpfolio.ui.site

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpfolio.data.ReaderRepository
import com.nbradbury.wpfolio.data.db.Origin
import com.nbradbury.wpfolio.data.db.PostEntity
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.ui
import com.nbradbury.wpfolio.ui.wpFolioViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SitePreviewViewModel(
    private val target: SiteSearchResult,
    private val repository: ReaderRepository,
) : ViewModel() {
    /** Describes the site for the header; it isn't saved unless the user subscribes. */
    val site = repository.previewSite(target, Origin.LOCAL, target.feedUrl)

    private val siteId = MutableStateFlow<String?>(null)
    val posts: StateFlow<List<PostEntity>> = siteId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.sitePosts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _subscribed = MutableStateFlow(false)
    val subscribed = _subscribed.asStateFlow()

    private val _subscribing = MutableStateFlow(false)
    val subscribing = _subscribing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            siteId.value = runCatching { repository.loadPreview(target) }.getOrNull()
            _loading.value = false
        }
    }

    fun subscribe() {
        if (_subscribed.value || _subscribing.value) return
        viewModelScope.launch {
            _subscribing.value = true
            try {
                repository.subscribe(target)
                _subscribed.value = true
            } catch (e: Exception) {
                _messages.send("Couldn't subscribe: ${e.message ?: "unknown error"}")
            } finally {
                _subscribing.value = false
            }
        }
    }
}

/** A site found from Add, shown with its recent posts before (or without) subscribing. */
@Composable
fun SitePreviewScreen(target: SiteSearchResult, onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    val viewModel = wpFolioViewModel(key = target.feedUrl) { SitePreviewViewModel(target, it.repository) }
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val subscribing by viewModel.subscribing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = WpFolio.colors

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    SiteLayout(
        site = viewModel.site,
        posts = posts,
        subscribed = subscribed,
        busy = subscribing,
        emptyContent = {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(color = colors.textSecondary)
                } else {
                    Text("No posts to show", style = ui(14f, 500), color = colors.textSecondary)
                }
            }
        },
        onSubscriptionClick = viewModel::subscribe,
        onBack = onBack,
        onOpenPost = onOpenPost,
    )
}
