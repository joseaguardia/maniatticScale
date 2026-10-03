/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.openscale.R

private const val BMI_MIN = 24f
private const val BMI_MAX = 45f
private val BMI_TICKS = listOf(25f, 30f, 35f, 40f)

private fun bmiFraction(bmi: Float): Float = ((bmi - BMI_MIN) / (BMI_MAX - BMI_MIN)).coerceIn(0f, 1f)

// Colours centred on the WHO bands: normal <25, overweight 25-30, obesity I/II/III from 30/35/40.
private val BMI_COLOR_STOPS = arrayOf(
    0f to Color(0xFF43A047),
    bmiFraction(24.6f) to Color(0xFF43A047),
    bmiFraction(27.5f) to Color(0xFFFDD835),
    bmiFraction(32.5f) to Color(0xFFFB8C00),
    bmiFraction(37.5f) to Color(0xFFE53935),
    bmiFraction(42f) to Color(0xFF8E1B1B),
    1f to Color(0xFF8E1B1B),
)

/**
 * Gradient bar for the BMI range 24-45 with an arrow and the value above it. Values outside the
 * range pin the arrow to the nearest end while the label still shows the real number.
 */
@Composable
fun BmiBar(bmi: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val label = stringResource(R.string.measurement_type_bmi)
    val valueText = remember(bmi) { "%.1f".format(bmi) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = onSurface, fontWeight = FontWeight.Bold)
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = onSurface.copy(alpha = 0.7f), fontSize = 10.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = "$label $valueText" }
    ) {
        val valueLayout = textMeasurer.measure("$label $valueText", valueStyle)
        val arrowHeight = 7.dp.toPx()
        val arrowHalfWidth = 6.dp.toPx()
        val barHeight = 12.dp.toPx()
        val tickLength = 4.dp.toPx()

        val barTop = valueLayout.size.height + arrowHeight + 2.dp.toPx()
        val barBottom = barTop + barHeight
        // Keep the arrow tip inside the bar's rounded ends.
        val inset = barHeight / 2
        val arrowX = inset + bmiFraction(bmi) * (size.width - 2 * inset)

        drawRoundRect(
            brush = Brush.horizontalGradient(*BMI_COLOR_STOPS, startX = 0f, endX = size.width),
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )

        BMI_TICKS.forEach { tick ->
            val x = inset + bmiFraction(tick) * (size.width - 2 * inset)
            drawLine(onSurface.copy(alpha = 0.5f), Offset(x, barBottom), Offset(x, barBottom + tickLength), 1.dp.toPx())
            val tickLayout = textMeasurer.measure(tick.toInt().toString(), tickStyle)
            drawText(tickLayout, topLeft = Offset(x - tickLayout.size.width / 2f, barBottom + tickLength))
        }

        val arrow = Path().apply {
            moveTo(arrowX - arrowHalfWidth, barTop - arrowHeight - 1.dp.toPx())
            lineTo(arrowX + arrowHalfWidth, barTop - arrowHeight - 1.dp.toPx())
            lineTo(arrowX, barTop - 1.dp.toPx())
            close()
        }
        drawPath(arrow, onSurface)

        val textX = (arrowX - valueLayout.size.width / 2f).coerceIn(0f, size.width - valueLayout.size.width)
        drawText(valueLayout, topLeft = Offset(textX, 0f))
    }
}
