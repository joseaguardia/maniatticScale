package com.health.openscale.ui.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.utils.LocaleUtils
import java.util.Locale

/** Latest weight as a bare number inside a gradient ring, meant to float over a corner of the chart. */
@Composable
fun CurrentWeightBubble(value: Float, unit: UnitType, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ring = Brush.sweepGradient(listOf(colors.primary, colors.tertiary, colors.primary))

    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(colors.surface)
            .border(width = 2.5.dp, brush = ring, shape = CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Stones need their "st lb" format; kg/lb show the number alone.
        val isStones = unit != UnitType.KG && unit != UnitType.LB
        val text = if (isStones) {
            LocaleUtils.formatValueForDisplay(value.toString(), unit)
        } else {
            LocaleUtils.formatNumber(value.toDouble(), maxFraction = 1, Locale.getDefault())
        }
        Text(
            text = text,
            style = (if (isStones) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLarge)
                .copy(letterSpacing = (-0.5).sp),
            fontWeight = FontWeight.ExtraBold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
