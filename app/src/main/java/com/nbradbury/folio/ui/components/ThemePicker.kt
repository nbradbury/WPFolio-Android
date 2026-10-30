package com.nbradbury.folio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nbradbury.folio.data.prefs.ThemeMode
import com.nbradbury.folio.ui.theme.DarkColors
import com.nbradbury.folio.ui.theme.Folio
import com.nbradbury.folio.ui.theme.LightColors
import com.nbradbury.folio.ui.theme.PaperColors
import com.nbradbury.folio.ui.theme.ui

/** Light / Paper / Dark / System swatches from the reader's Aa sheet. */
@Composable
fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            ThemeTile(mode, mode == selected, Modifier.weight(1f)) { onSelect(mode) }
        }
    }
}

@Composable
private fun ThemeTile(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Folio.colors
    val shape = RoundedCornerShape(14.dp)
    val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        modifier.clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, if (selected) colors.text else Color.Transparent, RoundedCornerShape(17.dp))
                .padding(3.dp)
        ) {
            val swatch = Modifier.fillMaxWidth().height(52.dp).clip(shape)
            when (mode) {
                ThemeMode.SYSTEM -> Box(
                    swatch
                        .background(Brush.linearGradient(0.5f to LightColors.background, 0.5f to DarkColors.articleBackground))
                        .border(1.dp, colors.outline, shape)
                )
                else -> {
                    val palette = when (mode) {
                        ThemeMode.PAPER -> PaperColors
                        ThemeMode.DARK -> DarkColors
                        else -> LightColors
                    }
                    Box(
                        swatch.background(palette.articleBackground).border(1.dp, colors.outline, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa", style = ui(16f, 600), color = palette.text)
                    }
                }
            }
        }
        Text(
            label,
            style = ui(12f, if (selected) 600 else 500),
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}
