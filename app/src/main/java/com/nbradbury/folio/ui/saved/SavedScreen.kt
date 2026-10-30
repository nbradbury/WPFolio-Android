package com.nbradbury.folio.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbradbury.folio.container
import com.nbradbury.folio.ui.feed.ListRow
import com.nbradbury.folio.ui.theme.Folio
import com.nbradbury.folio.ui.theme.SystemBarIcons
import com.nbradbury.folio.ui.theme.body
import com.nbradbury.folio.ui.theme.ui

@Composable
fun SavedScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    val context = LocalContext.current
    val posts by context.container.repository.saved.collectAsStateWithLifecycle(initialValue = null)
    val colors = Folio.colors
    SystemBarIcons(lightIcons = colors.isDark)

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Saved", style = ui(24f, 700, tracking = -0.02f), color = colors.text)
        }
        val saved = posts ?: return@Column
        if (saved.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Bookmark a post while reading to keep it here.", style = body(15f), color = colors.bodyMuted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                items(saved, key = { it.id }) { post -> ListRow(post) { onOpenPost(post.id) } }
            }
        }
    }
}
