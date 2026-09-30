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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.openscale.R
import com.health.openscale.core.data.AggregationLevel
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.data.UserGoals
import com.health.openscale.core.utils.LocaleUtils
import com.health.openscale.ui.theme.goalPathLime
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/** One Vico layer's worth of series: same role, same vertical axis. */
internal data class ChartLayerGroup(
    val role: ChartSeriesRole,
    val onRightAxis: Boolean,
    val series: List<ChartSeries>,
)

/**
 * Splits the series into the layers Vico draws, in a fixed order.
 *
 * The model producer and the layer list must agree on that order exactly — Vico pairs a layer with
 * its model by position — so both are built from this one function rather than from two parallel
 * chains of ifs that could drift apart.
 *
 * @param chartSeries    Smoothed/plain and projected series.
 * @param rawChartSeries Raw (unsmoothed) series, drawn as dots only while smoothing is active.
 * @param goalPaths      The straight goal paths built by [goalPathSeries].
 */
internal fun buildChartLayerGroups(
    chartSeries: List<ChartSeries>,
    rawChartSeries: List<ChartSeries>,
    goalPaths: List<ChartSeries>,
    isSmoothingActive: Boolean,
    showDataPointsSetting: Boolean,
): List<ChartLayerGroup> {
    val byRole = chartSeries.groupBy { it.role }
    val ordered = listOf(
        ChartSeriesRole.RAW to
                if (isSmoothingActive && showDataPointsSetting) rawChartSeries else emptyList(),
        ChartSeriesRole.ACTUAL to byRole[ChartSeriesRole.ACTUAL].orEmpty(),
        ChartSeriesRole.PROJECTED to byRole[ChartSeriesRole.PROJECTED].orEmpty(),
        ChartSeriesRole.GOAL_PATH to goalPaths,
    )

    return ordered.flatMap { (role, series) ->
        val (onLeft, onRight) = series.partition { !it.type.isOnRightYAxis }
        listOfNotNull(
            onLeft.takeIf { it.isNotEmpty() }
                ?.let { ChartLayerGroup(role, onRightAxis = false, series = it) },
            onRight.takeIf { it.isNotEmpty() }
                ?.let { ChartLayerGroup(role, onRightAxis = true, series = it) },
        )
    }
}

/**
 * Creates, remembers, and updates a [CartesianChartModelProducer] holding one Vico model per
 * entry of [layerGroups], in that order.
 */
@Composable
internal fun rememberChartModelProducer(
    layerGroups: List<ChartLayerGroup>,
): CartesianChartModelProducer {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(layerGroups) {
        modelProducer.runTransaction {
            layerGroups.forEach { group ->
                lineModel {
                    group.series.forEach { chartSeries ->
                        series(
                            x = chartSeries.points.map { it.x },
                            y = chartSeries.points.map { it.y },
                        )
                    }
                }
            }
        }
    }
    return modelProducer
}

/**
 * Creates and remembers one Vico layer per entry of [layerGroups], matching the models
 * [rememberChartModelProducer] produced from the same list.
 *
 * @param isSmoothingActive       Whether a smoothing algorithm is currently active.
 * @param showDataPointsSetting   Whether to show data point dots (when smoothing is off).
 * @param targetMeasurementTypeId If non-null, enables statistics mode (area fill, no points).
 * @param goalValuesForScaling    Goal values used to scale the Y-axis range.
 */
@Composable
internal fun rememberChartLayers(
    layerGroups: List<ChartLayerGroup>,
    isSmoothingActive: Boolean,
    showDataPointsSetting: Boolean,
    targetMeasurementTypeId: Int?,
    goalValuesForScaling: List<Float> = emptyList()
): List<LineCartesianLayer> {
    val goalValuesDouble = remember(goalValuesForScaling) {
        goalValuesForScaling.map { it.toDouble() }
    }

    val rangeProvider = remember(goalValuesDouble) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val effectiveMin = (goalValuesDouble + minY).minOrNull() ?: minY
                val delta = maxY - minY
                return if (delta == 0.0) effectiveMin - 1.0 else floor(effectiveMin - 0.1 * delta)
            }
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val effectiveMax = (goalValuesDouble + maxY).maxOrNull() ?: maxY
                val delta = maxY - minY
                return if (delta == 0.0) effectiveMax + 1.0 else ceil(effectiveMax + 0.1 * delta)
            }
        }
    }

    val layers = mutableListOf<LineCartesianLayer>()

    layerGroups.forEach { group ->
        // Keyed by role and axis so a layer keeps what it remembers when the groups around it
        // appear or disappear — the list grows and shrinks with the data.
        key(group.role, group.onRightAxis) {
            val colors = remember(group.series) { group.series.map { Color(it.type.color) } }
            val showPoints = when (group.role) {
                // A raw group exists only when smoothing is on and dots are enabled.
                ChartSeriesRole.RAW    -> true
                ChartSeriesRole.ACTUAL -> !isSmoothingActive && showDataPointsSetting
                else                   -> false
            }

            layers.add(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        colors.map { color ->
                            createLineSpec(
                                color          = color,
                                role           = group.role,
                                statisticsMode = targetMeasurementTypeId != null,
                                showPoints     = showPoints,
                            )
                        }
                    ),
                    verticalAxisPosition = if (group.onRightAxis) {
                        Axis.Position.Vertical.End
                    } else {
                        Axis.Position.Vertical.Start
                    },
                    rangeProvider = rangeProvider
                )
            )
        }
    }

    return layers
}

/**
 * Creates a [LineCartesianLayer.Line] specification for one layer group.
 *
 * @param color          The line and point color.
 * @param role           What the series says, which picks stroke, opacity and interpolation.
 * @param statisticsMode Adds area fill, hides points. Used when `targetMeasurementTypeId` is set.
 * @param showPoints     Whether to show dots on data points.
 */
internal fun createLineSpec(
    color: Color,
    role: ChartSeriesRole,
    statisticsMode: Boolean,
    showPoints: Boolean,
): LineCartesianLayer.Line {
    // The filled statistics look belongs to what was measured, never to what is merely expected.
    val filled = statisticsMode &&
            (role == ChartSeriesRole.RAW || role == ChartSeriesRole.ACTUAL)

    val lineStroke = when (role) {
        ChartSeriesRole.RAW       -> LineCartesianLayer.LineStroke.Dashed(dashLength = 0.dp)
        ChartSeriesRole.ACTUAL    -> LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp)
        ChartSeriesRole.PROJECTED -> LineCartesianLayer.LineStroke.Dashed(thickness = 2.dp, dashLength = 4.dp, gapLength = 4.dp)
        // Long dashes: told apart at a glance from the projection's short ones.
        ChartSeriesRole.GOAL_PATH -> LineCartesianLayer.LineStroke.Dashed(thickness = 1.dp, dashLength = 8.dp, gapLength = 6.dp)
    }

    val lineColor = when (role) {
        ChartSeriesRole.RAW       -> color.copy(alpha = 0.5f)
        ChartSeriesRole.GOAL_PATH -> goalPathLime
        else                      -> color
    }

    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        stroke = lineStroke,
        areaFill = if (filled) LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.2f))) else null,
        pointProvider = if (showPoints && !filled) {
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(ShapeComponent(Fill(color.copy(alpha = 0.7f)),shape = RoundedCornerShape(50)), size = 6.dp)
            )
        } else null,
        // A goal path is straight by definition; a curve drawn through its samples would lie.
        interpolator = if (role == ChartSeriesRole.GOAL_PATH) {
            LineCartesianLayer.Interpolator.Sharp
        } else {
            LineCartesianLayer.Interpolator.cubic()
        }
    )
}

/**
 * Remembers a [HorizontalLine] decoration for a given user goal.
 *
 * @param goal The goal to visualize.
 * @param type The corresponding [MeasurementType] for color and axis position.
 */
@Composable
internal fun rememberGoalLine(goal: UserGoals, type: MeasurementType?): HorizontalLine {
    val goalColor = type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface
    val goalFill = Fill(goalColor.copy(alpha = 0.7f))
    val line = rememberLineComponent(fill = goalFill, thickness = 2.dp)
    val labelTextColor = MaterialTheme.colorScheme.onPrimary
    val labelComponent = rememberTextComponent(
        style = TextStyle(color = labelTextColor),
        margins = Insets(start = 6.dp),
        padding = Insets(start = 8.dp, end = 8.dp, bottom = 2.dp, top = 2.dp),
        background = ShapeComponent(goalFill, shape = RoundedCornerShape(50)),
    )

    return remember(goal, line) {
        HorizontalLine(
            y = { goal.goalValue.toDouble() },
            line = line,
            labelComponent = labelComponent,
            label = {
                LocaleUtils.formatValueForDisplay(
                    goal.goalValue.toString(),
                    type?.unit ?: UnitType.NONE
                )
            },
            verticalAxisPosition = if (type?.isOnRightYAxis == true) Axis.Position.Vertical.End else Axis.Position.Vertical.Start
        )
    }
}

/**
 * Remembers and configures a [CartesianMarker] for chart interaction.
 *
 * @param valueFormatter The formatter for the value in the marker label.
 * @param showIndicator Whether to show a dot indicator on the line at the marker's position.
 */
@Composable
fun rememberMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter = DefaultCartesianMarker.ValueFormatter.default(),
    showIndicator: Boolean = true,
): CartesianMarker {
    val labelBackground = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(50),
        strokeThickness = 1.dp,
        strokeFill = Fill(MaterialTheme.colorScheme.outline),
    )
    val label = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = labelBackground,
        minWidth = TextComponent.MinWidth.fixed(40.dp),
    )
    val indicatorFrontComponent = rememberShapeComponent(Fill(MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(50))
    val guideline = rememberAxisGuidelineComponent()

    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        indicator = if (showIndicator) {
            { color ->
                LayeredComponent(
                    back = ShapeComponent(Fill(color.copy(alpha = 0.15f)), shape = RoundedCornerShape(50)),
                    front = LayeredComponent(
                        back = ShapeComponent(fill = Fill(color), shape = RoundedCornerShape(50)),
                        front = indicatorFrontComponent,
                        padding = Insets(5.dp),
                    ),
                    padding = Insets(10.dp),
                )
            }
        } else null,
        indicatorSize = 36.dp,
        guideline = guideline,
    )
}

/**
 * Remembers a [CartesianMarkerVisibilityListener] that triggers [onPointSelected]
 * with the timestamp of the last interacted chart point.
 */
@Composable
internal fun rememberMarkerVisibilityListener(
    chartSeries: List<ChartSeries>,
    onPointSelected: (timestamp: Long) -> Unit
): CartesianMarkerVisibilityListener {
    val lastX = remember { mutableStateOf<Float?>(null) }

    return remember(chartSeries, onPointSelected) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                lastX.value = targets.lastOrNull()?.x?.toFloat()
            }
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                lastX.value = targets.lastOrNull()?.x?.toFloat()
            }
            override fun onHidden(marker: CartesianMarker) {
                val x = lastX.value ?: return
                val point = chartSeries.flatMap { it.points }.find { it.x == x }
                val date = point?.date ?: LocalDate.ofEpochDay(x.toLong())
                onPointSelected(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }
        }
    }
}

/**
 * Remembers a [CartesianValueFormatter] for the X-axis that formats epoch day floats
 * back to human-readable date strings. The date format adapts to the current [aggregationLevel]:
 */
@Composable
internal fun rememberXAxisValueFormatter(
    chartSeries: List<ChartSeries>,
    aggregationLevel: AggregationLevel = AggregationLevel.NONE,
): CartesianValueFormatter {
    val weekAbbrev = stringResource(R.string.calendar_week_abbrev)
    return remember(chartSeries, aggregationLevel, weekAbbrev) {
        val xToDatesMap = chartSeries.flatMap { it.points }.associate { it.x to it.date }

        // The 'w' pattern letter resolves against the formatter's locale, which would let the
        // axis disagree with the table and overview. Build the week label from the shared rule
        // instead; the other levels have no such ambiguity.
        val weekFields = LocaleUtils.systemWeekFields()

        val pattern = when (aggregationLevel) {
            AggregationLevel.NONE  -> "d MMM"
            AggregationLevel.DAY   -> "d MMM"
            AggregationLevel.WEEK  -> null
            AggregationLevel.MONTH -> "MMM yy"
            AggregationLevel.YEAR  -> "yyyy"
        }

        val formatter = pattern?.let { DateTimeFormatter.ofPattern(it) }

        CartesianValueFormatter { _, value, _ ->
            val date = xToDatesMap[value.toFloat()] ?: LocalDate.ofEpochDay(value.toLong())
            formatter?.format(date)
                ?: "$weekAbbrev${date.get(weekFields.weekOfWeekBasedYear())} " +
                    "${date.get(weekFields.weekBasedYear()) % 100}"
        }
    }
}