package com.nbradbury.wpfolio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbradbury.wpfolio.AppContainer
import com.nbradbury.wpfolio.container

/** Creates a ViewModel scoped to the current destination, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> wpFolioViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(key = key) { create(container) }
}
