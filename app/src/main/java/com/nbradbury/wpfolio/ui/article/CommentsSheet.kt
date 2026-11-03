package com.nbradbury.wpfolio.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpfolio.data.comments.Comment
import com.nbradbury.wpfolio.data.comments.CommentThread
import com.nbradbury.wpfolio.data.comments.CommentsSource
import com.nbradbury.wpfolio.data.comments.ThreadedComment
import com.nbradbury.wpfolio.data.comments.threaded
import com.nbradbury.wpfolio.ui.components.SiteAvatar
import com.nbradbury.wpfolio.ui.components.accentFor
import com.nbradbury.wpfolio.ui.components.readableWidth
import com.nbradbury.wpfolio.ui.components.relativeTime
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.body
import com.nbradbury.wpfolio.ui.theme.ui
import com.nbradbury.wpfolio.ui.wpFolioViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val comments: List<ThreadedComment> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val replyingTo: Comment? = null,
    val posting: Boolean = false,
    /** Shown after posting, e.g. that the comment is awaiting moderation. */
    val notice: String? = null,
)

class CommentsViewModel(private val thread: CommentThread, private val source: CommentsSource) : ViewModel() {
    private val _state = MutableStateFlow(CommentsUiState())
    val state = _state.asStateFlow()

    private val loaded = mutableListOf<Comment>()
    private var nextPage = 1

    init {
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || (nextPage > 1 && !current.hasMore)) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                val page = source.list(thread, nextPage)
                loaded += page.comments
                nextPage++
                _state.update { it.copy(comments = threaded(loaded), hasMore = page.hasMore) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't load comments") }
            } finally {
                _state.update { it.copy(loading = false, loadingMore = false) }
            }
        }
    }

    fun replyTo(comment: Comment?) = _state.update { it.copy(replyingTo = comment, notice = null) }

    /** Posts [text]; [onPosted] runs once it's accepted, so the field can be cleared. */
    fun post(text: String, onPosted: () -> Unit) {
        if (_state.value.posting || text.isBlank()) return
        val parent = _state.value.replyingTo
        _state.update { it.copy(posting = true, notice = null) }
        viewModelScope.launch {
            try {
                val comment = source.create(thread, text.trim(), parent?.id)
                loaded += comment
                val notice = if (comment.held) "Your comment is awaiting moderation." else null
                _state.update { it.copy(comments = threaded(loaded), replyingTo = null, notice = notice) }
                onPosted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Couldn't post your comment") }
            } finally {
                _state.update { it.copy(posting = false) }
            }
        }
    }
}

/**
 * A post's comments in a full-height sheet, with a composer for signed-in WordPress.com readers.
 * The ViewModel lives in [owner] (the article page's store), so reopening the sheet is instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    thread: CommentThread,
    signedIn: Boolean,
    owner: ViewModelStoreOwner,
    onSignIn: () -> Unit,
    onCommentOnSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = wpFolioViewModel(key = "comments", owner = owner) { CommentsViewModel(thread, it.comments) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WpFolio.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).readableWidth().imePadding()) {
            Text(
                "Comments",
                style = ui(20f, 600, tracking = -0.01f),
                color = colors.text,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = colors.textSecondary,
                    )
                    state.comments.isEmpty() -> Text(
                        state.error ?: if (thread.canWrite) "No comments yet. Start the conversation." else NO_COMMENTS,
                        style = body(15f),
                        color = colors.bodyMuted,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> CommentList(state, canReply = thread.canWrite && signedIn, viewModel)
                }
            }
            HorizontalDivider(color = colors.divider)
            when {
                !thread.canWrite -> SheetAction("Comment on the site", onCommentOnSite)
                !signedIn -> SheetAction("Sign in to comment", onSignIn)
                else -> Composer(state, viewModel)
            }
        }
    }
}

@Composable
private fun CommentList(state: CommentsUiState, canReply: Boolean, viewModel: CommentsViewModel) {
    val colors = WpFolio.colors
    val links = rememberLinkStyles()
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.comments, key = { it.comment.id }) { (comment, depth) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = (22 + depth * REPLY_INDENT).dp, end = 22.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SiteAvatar(comment.avatarUrl, comment.author, accentFor(null, comment.author), 32.dp, initialSize = 14f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val time = comment.postedAt?.let { " · ${relativeTime(it)}" }.orEmpty()
                    Text("${comment.author}$time", style = ui(13.5f, 600), color = colors.text)
                    // fromHtml ends each paragraph with spacing, which would push Reply well below the text.
                    val text = rememberHtml(comment.html, links)
                    Text(
                        remember(text) { text.subSequence(0, text.trimEnd().length) },
                        style = body(15f, lineHeight = 1.5f),
                        color = colors.body,
                    )
                    if (comment.held) {
                        Text("Awaiting moderation", style = ui(12f, 500), color = colors.textSecondary)
                    } else if (canReply) {
                        Text(
                            "Reply",
                            style = ui(13f, 600),
                            color = colors.textSecondary,
                            modifier = Modifier.clickable(onClickLabel = "Reply to ${comment.author}") {
                                viewModel.replyTo(comment)
                            },
                        )
                    }
                }
            }
        }
        if (state.hasMore || state.error != null) {
            item(key = "more") {
                val label = when {
                    state.loadingMore -> "Loading…"
                    state.error != null -> "${state.error} · Retry"
                    else -> "Load more comments"
                }
                SheetAction(label, viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun Composer(state: CommentsUiState, viewModel: CommentsViewModel) {
    val colors = WpFolio.colors
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)) {
        val note = state.replyingTo?.let { "Replying to ${it.author}" } ?: state.notice
        if (note != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note,
                    style = ui(12.5f, 500),
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (state.replyingTo != null) {
                    IconButton(onClick = { viewModel.replyTo(null) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", tint = colors.textSecondary)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write a comment", style = body(15f), color = colors.textSecondary) },
                textStyle = body(15f).copy(color = colors.text),
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.divider,
                    cursorColor = colors.text,
                ),
                modifier = Modifier.weight(1f),
            )
            if (state.posting) {
                CircularProgressIndicator(
                    Modifier.padding(12.dp).size(24.dp),
                    color = colors.textSecondary,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { viewModel.post(text) { text = "" } }, enabled = text.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Post comment",
                        tint = if (text.isNotBlank()) colors.text else colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    val colors = WpFolio.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = ui(14.5f, 600),
            color = colors.text,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private const val NO_COMMENTS = "No comments yet."

/** How far replies are indented, in dp. */
private const val REPLY_INDENT = 30
