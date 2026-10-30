package com.nbradbury.wpreadme.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nbradbury.wpreadme.data.prefs.ReaderSettings
import com.nbradbury.wpreadme.data.prefs.ThemeMode
import com.nbradbury.wpreadme.ui.components.ThemePicker
import com.nbradbury.wpreadme.ui.theme.WpReadMe
import com.nbradbury.wpreadme.ui.theme.body
import com.nbradbury.wpreadme.ui.theme.ui

const val MIN_TEXT_SCALE = 0.85f
const val MAX_TEXT_SCALE = 1.4f

/** 1e: text size and theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onTextScale: (Float) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WpReadMe.colors
    var scale by remember { mutableFloatStateOf(settings.textScale) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheet, scrimColor = Color.Black.copy(alpha = 0.45f)) {
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Text size", style = ui(13f, 600), color = colors.bodyMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("A", style = body(14f, weight = 500), color = colors.text)
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        onValueChangeFinished = { onTextScale(scale) },
                        valueRange = MIN_TEXT_SCALE..MAX_TEXT_SCALE,
                        thumb = {
                            Box(Modifier.size(22.dp).shadow(3.dp, CircleShape).background(colors.text, CircleShape))
                        },
                        track = { state ->
                            val range = state.valueRange
                            val fraction = (state.value - range.start) / (range.endInclusive - range.start)
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.text.copy(alpha = 0.14f))) {
                                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.text))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text("A", style = body(24f, weight = 500), color = colors.text)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme", style = ui(13f, 600), color = colors.bodyMuted)
                ThemePicker(settings.theme, onTheme)
            }
        }
    }
}
