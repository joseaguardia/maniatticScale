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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.ui.screen.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.service.ChangeComparison
import com.health.openscale.core.service.MeasurementChange
import com.health.openscale.core.utils.LocaleUtils
import com.health.openscale.ui.components.RoundMeasurementIcon
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.shared.SharedViewModel
import com.health.openscale.ui.shared.TopBarAction
import com.health.openscale.ui.theme.success
import kotlin.math.abs

/** Below this, a change reads as none and is not colored. */
private const val NO_CHANGE_THRESHOLD = 0.005f

/** Which direction counts as progress for each summarized type. */
private val IMPROVEMENT_DIRECTION: Map<MeasurementType.Key<*>, Int> = mapOf(
    MeasurementType.WEIGHT to -1,
    MeasurementType.BODY_FAT to -1,
    MeasurementType.LBM to 1,
    MeasurementType.HIPS to -1,
    MeasurementType.WAIST to -1,
    MeasurementType.CHEST to -1,
)

/**
 * Shown after saving a new measurement: its changes since the previous entry (top half) and since
 * the reference date configured in the general settings (bottom half).
 */
@Composable
fun MeasurementSummaryScreen(
    navController: NavController,
    userId: Int,
    timestamp: Long,
    sharedViewModel: SharedViewModel,
) {
    val resources = LocalResources.current
    val summary by remember(userId, timestamp) { sharedViewModel.changeSummaryFlow(userId, timestamp) }
        .collectAsState(initial = null)
    val referenceDate by sharedViewModel.summaryReferenceDate.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(resources.getString(R.string.title_measurement_summary))
        sharedViewModel.setTopBarActions(
            listOf(
                TopBarAction(
                    icon = Icons.Default.Check,
                    contentDescription = resources.getString(R.string.dialog_ok),
                    onClick = { navController.popBackStack() }
                )
            )
        )
    }

    val current = summary
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Each card takes the height its content needs; the scroll only kicks in if a large font
    // setting ever makes the content taller than the screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummarySection(
            title = stringResource(R.string.summary_since_previous),
            comparison = current.sincePrevious,
            emptyText = stringResource(R.string.summary_no_previous),
        )

        val sinceReference = current.sinceReference
        if (sinceReference != null) {
            SummarySection(
                title = stringResource(
                    R.string.summary_since_reference,
                    referenceDate?.let { LocaleUtils.formatCompactDate(it) } ?: ""
                ),
                comparison = sinceReference,
                emptyText = stringResource(R.string.summary_no_values),
            )
        } else {
            ReferenceNotSetCard(navController)
        }
    }
}

/** Progress since the configured reference date: each type's latest reading against that date. */
@Composable
fun GoalProgressScreen(
    navController: NavController,
    userId: Int,
    sharedViewModel: SharedViewModel,
) {
    val resources = LocalResources.current
    val state by remember(userId) { sharedViewModel.goalProgressFlow(userId) }
        .collectAsState(initial = null)

    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(resources.getString(R.string.title_goal_progress))
        sharedViewModel.setTopBarActions(emptyList())
    }

    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val (referenceDate, progress) = current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (referenceDate == null) {
            ReferenceNotSetCard(navController)
        } else {
            SummarySection(
                title = stringResource(R.string.summary_since_reference, LocaleUtils.formatCompactDate(referenceDate)),
                comparison = progress ?: ChangeComparison(baselineTimestamp = null, changes = emptyList()),
                emptyText = stringResource(R.string.summary_no_values),
            )
        }
    }
}

@Composable
private fun ReferenceNotSetCard(navController: NavController) {
    SummaryCard {
        Text(
            text = stringResource(R.string.summary_reference_not_set),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { navController.navigate(Routes.GENERAL_SETTINGS) }) {
            Text(stringResource(R.string.summary_configure))
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Weight on top, body composition in the middle, circumferences at the bottom. */
private val SUMMARY_LEVELS: List<List<MeasurementType.Key<*>>> = listOf(
    listOf(MeasurementType.WEIGHT),
    listOf(MeasurementType.BODY_FAT, MeasurementType.LBM),
    listOf(MeasurementType.HIPS, MeasurementType.WAIST, MeasurementType.CHEST),
)

@Composable
private fun SummarySection(
    title: String,
    comparison: ChangeComparison,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    SummaryCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            comparison.baselineTimestamp?.let { ts ->
                Text(
                    text = LocaleUtils.formatCompactDate(LocaleUtils.toLocalDate(ts)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (comparison.baselineTimestamp == null || comparison.changes.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUMMARY_LEVELS.forEachIndexed { index, keys ->
                    val levelChanges = keys.mapNotNull { key -> comparison.changes.firstOrNull { it.type.key == key } }
                    if (levelChanges.isNotEmpty()) {
                        // Tiles of one level share the tallest one's height.
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            levelChanges.forEach { change ->
                                ChangeTile(
                                    change = change,
                                    prominent = index == 0,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeTile(
    change: MeasurementChange,
    prominent: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unit = change.type.unit
    val delta = change.delta

    val deltaText = when {
        delta == null -> stringResource(R.string.placeholder_empty_value)
        abs(delta) < NO_CHANGE_THRESHOLD -> LocaleUtils.formatValueForDisplay("0", unit)
        else -> LocaleUtils.formatValueForDisplay(delta.toString(), unit, includeSign = true)
    }
    val direction = change.type.key?.let { IMPROVEMENT_DIRECTION[it] } ?: 0
    val deltaColor = when {
        delta == null || abs(delta) < NO_CHANGE_THRESHOLD || direction == 0 ->
            MaterialTheme.colorScheme.onSurface
        delta * direction > 0 -> MaterialTheme.colorScheme.success
        else -> MaterialTheme.colorScheme.error
    }
    val typeName = change.type.getDisplayName(context)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics(mergeDescendants = true) { contentDescription = "$typeName $deltaText" }
            .padding(vertical = if (prominent) 16.dp else 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (prominent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundMeasurementIcon(
                    icon = change.type.icon.resource,
                    size = 44.dp,
                    backgroundTint = Color(change.type.color),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor,
                        maxLines = 1
                    )
                    TileLabel(typeName)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoundMeasurementIcon(
                    icon = change.type.icon.resource,
                    size = 28.dp,
                    backgroundTint = Color(change.type.color),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = deltaColor,
                    maxLines = 1
                )
                TileLabel(typeName)
            }
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
