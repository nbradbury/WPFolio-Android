package com.nbradbury.wpfolio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbradbury.wpfolio.AppContainer
import com.nbradbury.wpfolio.container

/** Creates a ViewModel scoped to the current destination, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> wpFolioViewModel(
    key: String? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(viewModelStoreOwner = owner, key = key) { create(container) }
}

/**
 * A ViewModel store for one item of a list or pager, cleared when the item leaves composition, so
 * paging through many items doesn't keep every one's ViewModel until the screen closes.
 */
@Composable
fun rememberItemViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}
