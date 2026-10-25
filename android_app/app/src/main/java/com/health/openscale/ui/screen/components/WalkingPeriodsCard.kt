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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.health.openscale.R
import com.health.openscale.core.model.WalkingSummary
import com.health.openscale.core.model.WalkingSummaryState
import com.health.openscale.ui.shared.SharedViewModel

@Composable
fun walkingDurationText(totalMinutes: Long): String {
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return if (hours > 0) stringResource(R.string.summary_walking_hours_minutes, hours, minutes)
    else stringResource(R.string.summary_walking_minutes, minutes)
}

@Composable
fun walkingDistanceText(meters: Double): String =
    stringResource(R.string.summary_walking_km, "%.1f".format(meters / 1000.0))

/**
 * Walking time and distance of the last 7 and 30 days, read from Health Connect when the card
 * enters the screen. Hidden while loading or when Health Connect is not available on the device.
 */
@Composable
fun WalkingPeriodsCard(sharedViewModel: SharedViewModel, modifier: Modifier = Modifier) {
    val state by sharedViewModel.overviewWalking.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { sharedViewModel.refreshOverviewWalking() }

    LaunchedEffect(Unit) { sharedViewModel.refreshOverviewWalking() }

    val current = state
    if (current == null || current == WalkingSummaryState.Unavailable) return

    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.overview_walking_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            when (current) {
                WalkingSummaryState.PermissionRequired ->
                    TextButton(
                        onClick = { permissionLauncher.launch(sharedViewModel.healthConnectPermissions) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.walking_connect))
                    }

                WalkingSummaryState.Error -> Text(
                    text = stringResource(R.string.walking_error),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                is WalkingSummaryState.Loaded -> Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 6.dp),
                ) {
                    current.summaries.forEachIndexed { index, summary ->
                        if (index > 0) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        PeriodColumn(summary, Modifier.weight(1f))
                    }
                }

                WalkingSummaryState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun PeriodColumn(summary: WalkingSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.overview_walking_last_days, summary.days),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        IconValue(Icons.Default.Timer, walkingDurationText(summary.totalDurationMinutes))
        IconValue(Icons.AutoMirrored.Filled.DirectionsWalk, walkingDistanceText(summary.totalDistanceMeters))
        Spacer(Modifier.size(2.dp))
        Text(
            text = stringResource(
                R.string.overview_walking_daily_average,
                walkingDurationText(Math.round(summary.totalDurationMinutes.toDouble() / summary.days)),
                walkingDistanceText(summary.totalDistanceMeters / summary.days),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconValue(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
