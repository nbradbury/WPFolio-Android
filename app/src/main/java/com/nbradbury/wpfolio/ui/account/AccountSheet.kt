package com.nbradbury.wpfolio.ui.account

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nbradbury.wpfolio.AppContainer
import com.nbradbury.wpfolio.ui.components.AccountAvatar
import com.nbradbury.wpfolio.ui.wpFolioViewModel
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.ui
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    val account = repository.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val localCount = repository.localSiteCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** [onDone] runs once the account is gone, so the next screen sees the user signed out. */
    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.signOut()
            onDone()
        }
    }

    fun moveLocalFeeds(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(runCatching { repository.moveLocalFeedsToWpcom() }.getOrDefault(0)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onOpenSaved: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val viewModel = wpFolioViewModel { AccountViewModel(it) }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val localCount by viewModel.localCount.collectAsStateWithLifecycle()
    val colors = WpFolio.colors
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheet) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val current = account
            if (current != null) {
                Row(
                    Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountAvatar(current, onClick = {}, size = 44.dp)
                    Column {
                        Text(current.displayName.ifBlank { "WordPress.com" }, style = ui(16f, 600), color = colors.text)
                        Text("Signed in to WordPress.com", style = ui(13f, 500), color = colors.textSecondary)
                    }
                }
                if (localCount > 0) {
                    SheetRow(Icons.Rounded.CloudUpload, "Move $localCount RSS ${if (localCount == 1) "feed" else "feeds"} to WordPress.com") {
                        viewModel.moveLocalFeeds { moved ->
                            Toast.makeText(context, "Moved $moved to WordPress.com", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                SheetRow(Icons.AutoMirrored.Rounded.Login, "Sign in with WordPress.com") {
                    onDismiss()
                    onSignIn()
                }
            }
            SheetRow(Icons.Rounded.Bookmark, "Saved", onClick = onOpenSaved)
            if (current != null) {
                SheetRow(Icons.AutoMirrored.Rounded.Logout, "Sign out") {
                    onDismiss()
                    viewModel.signOut(onSignedOut)
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = WpFolio.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Text(label, style = ui(15f, 500), color = colors.text)
    }
}
