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
package com.health.openscale.core.service

import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.testutil.Fixtures
import org.junit.Test

class MeasurementChangeCalculatorTest {

    private val weight = Fixtures.type(id = 1, identity = MeasurementType.WEIGHT.identity)
    private val waist = Fixtures.type(id = 2, identity = MeasurementType.WAIST.identity)
    private val other = Fixtures.type(id = 3)
    private val types = listOf(other, waist, weight)

    private val day = 86_400_000L

    @Test
    fun missingMeasurement_isNull() {
        assertThat(MeasurementChangeCalculator.compute(emptyList(), types, 10 * day, null)).isNull()
    }

    @Test
    fun firstEntry_hasNoPreviousBaseline() {
        val current = Fixtures.mwv(1, 10 * day, listOf(Fixtures.valueWithType(weight, 80f)))

        val summary = MeasurementChangeCalculator.compute(listOf(current), types, 10 * day, null)!!

        assertThat(summary.sincePrevious.baselineTimestamp).isNull()
        assertThat(summary.sincePrevious.changes.single().delta).isNull()
        assertThat(summary.sinceReference).isNull()
    }

    @Test
    fun sincePrevious_usesLatestEarlierReadingPerType_inSummaryOrder() {
        val measurements = listOf(
            Fixtures.mwv(1, 1 * day, listOf(Fixtures.valueWithType(weight, 85f), Fixtures.valueWithType(waist, 95f))),
            Fixtures.mwv(2, 5 * day, listOf(Fixtures.valueWithType(weight, 82f))),
            Fixtures.mwv(3, 10 * day, listOf(Fixtures.valueWithType(waist, 92f), Fixtures.valueWithType(weight, 81f), Fixtures.valueWithType(other, 1f))),
            Fixtures.mwv(4, 20 * day, listOf(Fixtures.valueWithType(weight, 70f))),
        )

        val previous = MeasurementChangeCalculator.compute(measurements, types, 10 * day, null)!!.sincePrevious

        assertThat(previous.baselineTimestamp).isEqualTo(5 * day)
        assertThat(previous.changes.map { it.type }).containsExactly(weight, waist).inOrder()
        assertThat(previous.changes[0].delta).isWithin(1e-4f).of(-1f)
        assertThat(previous.changes[1].baselineTimestamp).isEqualTo(1 * day)
        assertThat(previous.changes[1].delta).isWithin(1e-4f).of(-3f)
    }

    @Test
    fun sinceReference_usesReadingNearestToDate_excludingCurrent() {
        val measurements = listOf(
            Fixtures.mwv(1, 1 * day, listOf(Fixtures.valueWithType(weight, 90f))),
            Fixtures.mwv(2, 4 * day, listOf(Fixtures.valueWithType(weight, 88f))),
            Fixtures.mwv(3, 10 * day, listOf(Fixtures.valueWithType(weight, 81f))),
        )

        val reference = MeasurementChangeCalculator.compute(measurements, types, 10 * day, 3 * day)!!.sinceReference!!

        assertThat(reference.baselineTimestamp).isEqualTo(4 * day)
        assertThat(reference.changes.single().delta).isWithin(1e-4f).of(-7f)
    }

    @Test
    fun progress_comparesLatestReadingPerType_againstReadingNearestToReference() {
        val measurements = listOf(
            Fixtures.mwv(1, 1 * day, listOf(Fixtures.valueWithType(weight, 90f), Fixtures.valueWithType(waist, 100f))),
            Fixtures.mwv(2, 6 * day, listOf(Fixtures.valueWithType(weight, 87f), Fixtures.valueWithType(waist, 97f))),
            Fixtures.mwv(3, 10 * day, listOf(Fixtures.valueWithType(weight, 85f))),
        )

        val progress = MeasurementChangeCalculator.computeProgress(measurements, types, 2 * day)!!

        assertThat(progress.baselineTimestamp).isEqualTo(1 * day)
        assertThat(progress.changes[0].delta).isWithin(1e-4f).of(-5f)
        assertThat(progress.changes[1].delta).isWithin(1e-4f).of(-3f)
    }

    @Test
    fun progress_withoutMeasurements_isNull() {
        assertThat(MeasurementChangeCalculator.computeProgress(emptyList(), types, 2 * day)).isNull()
    }
}
