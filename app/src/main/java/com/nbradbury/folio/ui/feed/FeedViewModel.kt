package com.nbradbury.folio.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nbradbury.folio.data.ReaderRepository
import com.nbradbury.folio.data.SessionExpiredException
import com.nbradbury.folio.data.db.PostEntity
import com.nbradbury.folio.data.prefs.WpcomAccount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val posts: List<PostEntity> = emptyList(),
    val unread: Int = 0,
    val account: WpcomAccount? = null,
    val loaded: Boolean = false,
)

class FeedViewModel(private val repository: ReaderRepository) : ViewModel() {
    val state: StateFlow<FeedUiState> =
        combine(repository.feed(), repository.unreadCount, repository.account) { posts, unread, account ->
            FeedUiState(posts, unread, account, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadingMore = false
    private var hasMore = true

    init {
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val failures = repository.refresh()
                hasMore = true
                if (failures > 0) _messages.send("Couldn't refresh $failures ${if (failures == 1) "site" else "sites"}")
            } catch (e: SessionExpiredException) {
                _messages.send(e.message.orEmpty())
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore || _refreshing.value) return
        loadingMore = true
        viewModelScope.launch {
            hasMore = runCatching { repository.loadMore() }.getOrDefault(false)
            loadingMore = false
        }
    }
}
