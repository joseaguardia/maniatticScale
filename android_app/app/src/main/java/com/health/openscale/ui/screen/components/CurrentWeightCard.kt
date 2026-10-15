package com.health.openscale.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.openscale.R
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.utils.LocaleUtils
import java.util.Locale

/** Latest weight as a large number with its unit, sized to sit next to [BmiBar]. */
@Composable
fun CurrentWeightCard(value: Float, unit: UnitType, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.measurement_type_weight),
                style = MaterialTheme.typography.labelSmall,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                // Stones carry their own "st lb" format, so only kg/lb split number and unit.
                if (unit == UnitType.KG || unit == UnitType.LB) {
                    Text(
                        text = LocaleUtils.formatNumber(value.toDouble(), maxFraction = 1, Locale.getDefault()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                } else {
                    Text(
                        text = LocaleUtils.formatValueForDisplay(value.toString(), unit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
