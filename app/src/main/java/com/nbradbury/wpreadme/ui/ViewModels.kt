package com.nbradbury.wpreadme.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbradbury.wpreadme.AppContainer
import com.nbradbury.wpreadme.container

/** Creates a ViewModel scoped to the current destination, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> wpReadMeViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(key = key) { create(container) }
}
