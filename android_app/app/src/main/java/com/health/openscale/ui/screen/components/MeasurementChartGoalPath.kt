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

import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.usecase.GoalProgress
import java.time.LocalDate

/**
 * The straight path each goal implies: from the reading its start date resolved to, down (or up)
 * to the target value on the target date. Read against the measured curve it answers "am I ahead
 * or behind", which a flat line at the target value alone cannot.
 *
 * Drawn in full, so the chart's x-axis grows to cover the whole goal. Clipping it to the span the
 * readings already occupy would erase the ordinary case outright — a goal set today and aimed at a
 * date months out lies entirely to the right of every measurement.
 *
 * A goal without a target date has no second endpoint and is skipped; the horizontal goal line
 * marks its target value instead.
 *
 * The path carries a point at every plotted x inside its span, not just its two ends, so the chart
 * marker reports where the plan says the user should be on the day they tapped.
 *
 * @param progress Goal progress for the user, at most one entry per measurement type.
 * @param types    The types currently plotted; goals on any other type are ignored.
 * @param plottedX Ascending, distinct x values (epoch days) the chart already draws.
 */
internal fun goalPathSeries(
    progress: List<GoalProgress>,
    types: List<MeasurementType>,
    plottedX: List<Float>,
): List<ChartSeries> {
    if (progress.isEmpty() || types.isEmpty()) return emptyList()

    val typeById = types.associateBy { it.id }

    return progress.mapNotNull { goal ->
        val type = typeById[goal.type.id] ?: return@mapNotNull null
        val targetDate = goal.goalTargetDate ?: return@mapNotNull null

        val startX = goal.startDate.toEpochDay().toFloat()
        val targetX = targetDate.toEpochDay().toFloat()
        // A target on or before the start point describes no span to draw across.
        if (targetX <= startX) return@mapNotNull null

        val slope = (goal.goalValue - goal.startValue) / (targetX - startX)
        val xs = (listOf(startX, targetX) + plottedX.filter { it > startX && it < targetX })
            .distinct()
            .sorted()

        ChartSeries(
            role = ChartSeriesRole.GOAL_PATH,
            type = type,
            points = xs.map { x ->
                ChartPoint(
                    date = LocalDate.ofEpochDay(x.toLong()),
                    x = x,
                    y = goal.startValue + (x - startX) * slope,
                )
            },
        )
    }
}
