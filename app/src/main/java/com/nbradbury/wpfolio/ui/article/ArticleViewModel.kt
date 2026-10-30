package com.nbradbury.wpfolio.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpfolio.AppContainer
import com.nbradbury.wpfolio.data.db.PostEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModel(private val postId: String, private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val post: StateFlow<PostEntity?> = repository.post(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Posts opened from a site preview belong to no subscription, so there's no site screen. */
    val siteSubscribed: StateFlow<Boolean> = repository.post(postId)
        .filterNotNull()
        .map { it.siteId }
        .distinctUntilChanged()
        .flatMapLatest { repository.site(it) }
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val blocks: StateFlow<List<Block>?> = repository.post(postId)
        .filterNotNull()
        .map { Triple(it.contentHtml, it.imageUrl, it.url) }
        .distinctUntilChanged()
        .map { (html, hero, url) -> ArticleBlocks.parse(html, url).withoutHero(hero) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { repository.markRead(postId) }
    }

    fun toggleSaved() {
        viewModelScope.launch { repository.toggleSaved(postId) }
    }

    fun toggleLiked() {
        viewModelScope.launch {
            runCatching { repository.toggleLiked(postId) }.onFailure { _messages.send("Couldn't update the like") }
        }
    }

}

/** The hero already shows the featured image, so drop its first repeat in the body. */
private fun List<Block>.withoutHero(hero: String?): List<Block> {
    if (hero == null) return this
    val key = hero.substringBefore('?')
    val index = indexOfFirst { it is Block.Image && it.url.substringBefore('?') == key }
    return if (index in 0..2) filterIndexed { i, _ -> i != index } else this
}
