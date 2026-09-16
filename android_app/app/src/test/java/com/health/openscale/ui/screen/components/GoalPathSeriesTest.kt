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

import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.usecase.GoalProgress
import com.health.openscale.testutil.Fixtures
import java.time.LocalDate
import org.junit.Test

/**
 * Pure JVM tests for [goalPathSeries] — the straight line a goal draws across the chart. No Vico
 * and no Compose: the geometry is a pure function of the goal and the span already plotted.
 */
class GoalPathSeriesTest {

    private val weight = Fixtures.type(id = 1, identity = MeasurementType.WEIGHT.identity)

    /** 1 Jan → 31 Jan, 90 kg → 80 kg: a tidy 10 kg over 30 days. */
    private fun goal(
        type: MeasurementType = weight,
        startDate: LocalDate = LocalDate.of(2025, 1, 1),
        startValue: Float = 90f,
        goalValue: Float = 80f,
        targetDate: LocalDate? = LocalDate.of(2025, 1, 31),
    ) = GoalProgress(
        type = type,
        startValue = startValue,
        startDate = startDate,
        today = LocalDate.of(2025, 1, 15),
        currentValue = startValue,
        delta = 0f,
        goalValue = goalValue,
        goalTargetDate = targetDate,
        goalFraction = 0f,
        daysToTarget = 16,
        inGoalSince = null,
        remainingToGoal = goalValue - startValue,
    )

    private fun x(date: LocalDate) = date.toEpochDay().toFloat()

    private fun days(vararg dates: LocalDate) = dates.map { x(it) }.sorted()

    @Test
    fun `path runs from the start point to the target`() {
        val plotted = days(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))

        val series = goalPathSeries(listOf(goal()), listOf(weight), plotted)

        assertThat(series).hasSize(1)
        assertThat(series.single().role).isEqualTo(ChartSeriesRole.GOAL_PATH)
        val points = series.single().points
        assertThat(points.first().x).isEqualTo(x(LocalDate.of(2025, 1, 1)))
        assertThat(points.first().y).isWithin(1e-4f).of(90f)
        assertThat(points.last().x).isEqualTo(x(LocalDate.of(2025, 1, 31)))
        assertThat(points.last().y).isWithin(1e-4f).of(80f)
    }

    /**
     * The ordinary case: a goal set today, aimed months out. Its whole span lies to the right of
     * every reading, so anything that trimmed the path to the plotted range would erase it.
     */
    @Test
    fun `path survives a goal that lies entirely after the last reading`() {
        val plotted = days(LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 20))

        val points = goalPathSeries(listOf(goal()), listOf(weight), plotted).single().points

        assertThat(points.first().x).isEqualTo(x(LocalDate.of(2025, 1, 1)))
        assertThat(points.last().x).isEqualTo(x(LocalDate.of(2025, 1, 31)))
    }

    /** Likewise for a goal that started before anything currently plotted. */
    @Test
    fun `path reaches back to a start point earlier than the plotted range`() {
        val plotted = days(LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 25))

        val points = goalPathSeries(listOf(goal()), listOf(weight), plotted).single().points

        assertThat(points.first().x).isEqualTo(x(LocalDate.of(2025, 1, 1)))
        assertThat(points.first().y).isWithin(1e-4f).of(90f)
    }

    @Test
    fun `path carries a point at every plotted day inside its span`() {
        val plotted = days(
            LocalDate.of(2025, 1, 7),
            LocalDate.of(2025, 1, 14),
        )

        val points = goalPathSeries(listOf(goal()), listOf(weight), plotted).single().points

        assertThat(points.map { it.x }).isEqualTo(
            days(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                LocalDate.of(2025, 1, 14),
                LocalDate.of(2025, 1, 31),
            )
        )
        // Every sample sits on the same straight line: 90 kg falling 10 kg over 30 days.
        assertThat(points[1].y).isWithin(1e-3f).of(88f)
        assertThat(points[2].y).isWithin(1e-3f).of(85.6667f)
    }

    @Test
    fun `plotted days outside the span add no samples`() {
        val plotted = days(LocalDate.of(2024, 11, 1), LocalDate.of(2025, 6, 1))

        val points = goalPathSeries(listOf(goal()), listOf(weight), plotted).single().points

        assertThat(points).hasSize(2)
    }

    @Test
    fun `path needs no plotted data at all`() {
        val points = goalPathSeries(listOf(goal()), listOf(weight), emptyList()).single().points

        assertThat(points).hasSize(2)
        assertThat(points.first().y).isWithin(1e-4f).of(90f)
        assertThat(points.last().y).isWithin(1e-4f).of(80f)
    }

    @Test
    fun `goal without a target date has no path`() {
        val plotted = days(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))

        assertThat(goalPathSeries(listOf(goal(targetDate = null)), listOf(weight), plotted)).isEmpty()
    }

    @Test
    fun `goal on a type that is not plotted is ignored`() {
        val other = Fixtures.type(id = 2, identity = MeasurementType.BODY_FAT.identity)
        val plotted = days(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))

        assertThat(goalPathSeries(listOf(goal(type = other)), listOf(weight), plotted)).isEmpty()
    }

    @Test
    fun `target date on or before the start point has no path`() {
        val plotted = days(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        val backwards = goal(targetDate = LocalDate.of(2024, 12, 20))

        assertThat(goalPathSeries(listOf(backwards), listOf(weight), plotted)).isEmpty()
    }

    @Test
    fun `a rising goal slopes upwards`() {
        val plotted = days(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        val gaining = goal(startValue = 60f, goalValue = 66f)

        val points = goalPathSeries(listOf(gaining), listOf(weight), plotted).single().points

        assertThat(points.first().y).isWithin(1e-4f).of(60f)
        assertThat(points.last().y).isWithin(1e-4f).of(66f)
    }
}
