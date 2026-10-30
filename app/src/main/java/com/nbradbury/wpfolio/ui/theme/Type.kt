package com.nbradbury.wpfolio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nbradbury.wpfolio.R

private fun variableFamily(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Interface face. */
val InstrumentSans = variableFamily(R.font.instrument_sans, 400, 500, 600, 700)

/** Reading face, built for legibility. */
val AtkinsonNext = variableFamily(R.font.atkinson_hyperlegible_next, 400, 500, 600, 700)

/** Interface text in Instrument Sans; sizes and tracking follow the design's CSS. */
fun ui(
    size: Float,
    weight: Int = 400,
    lineHeight: Float? = null,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = InstrumentSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight?.em ?: TextUnit.Unspecified,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
)

/** Reading text in Atkinson Hyperlegible Next. */
fun body(size: Float, lineHeight: Float = 1.5f) = TextStyle(
    fontFamily = AtkinsonNext,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.em,
)

val WpFolioTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = InstrumentSans),
        displayMedium = displayMedium.copy(fontFamily = InstrumentSans),
        displaySmall = displaySmall.copy(fontFamily = InstrumentSans),
        headlineLarge = headlineLarge.copy(fontFamily = InstrumentSans),
        headlineMedium = headlineMedium.copy(fontFamily = InstrumentSans),
        headlineSmall = headlineSmall.copy(fontFamily = InstrumentSans),
        titleLarge = titleLarge.copy(fontFamily = InstrumentSans),
        titleMedium = titleMedium.copy(fontFamily = InstrumentSans),
        titleSmall = titleSmall.copy(fontFamily = InstrumentSans),
        bodyLarge = bodyLarge.copy(fontFamily = InstrumentSans),
        bodyMedium = bodyMedium.copy(fontFamily = InstrumentSans),
        bodySmall = bodySmall.copy(fontFamily = InstrumentSans),
        labelLarge = labelLarge.copy(fontFamily = InstrumentSans),
        labelMedium = labelMedium.copy(fontFamily = InstrumentSans),
        labelSmall = labelSmall.copy(fontFamily = InstrumentSans),
    )
}
