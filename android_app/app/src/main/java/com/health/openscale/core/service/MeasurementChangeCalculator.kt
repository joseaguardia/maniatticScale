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
package com.health.openscale.core.service

import com.health.openscale.core.data.InputFieldType
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.model.MeasurementWithValues
import kotlin.math.abs

/** One measurement type of a freshly saved measurement compared against a baseline reading. */
data class MeasurementChange(
    val type: MeasurementType,
    val currentValue: Float,
    /** Null when no baseline reading carries this type. */
    val baselineValue: Float?,
    val baselineTimestamp: Long?,
) {
    val delta: Float? get() = baselineValue?.let { currentValue - it }
}

/** The changes of a measurement against one baseline (previous entry or a reference date). */
data class ChangeComparison(
    /** Timestamp of the measurement the baseline resolved to; null when there is none. */
    val baselineTimestamp: Long?,
    val changes: List<MeasurementChange>,
)

data class MeasurementChangeSummary(
    val timestamp: Long,
    val sincePrevious: ChangeComparison,
    /** Null when no reference date is configured. */
    val sinceReference: ChangeComparison?,
)

/** Builds the post-save summary comparing a measurement against earlier ones. */
object MeasurementChangeCalculator {

    /** The types the summary reports, in display order. */
    val SUMMARY_KEYS: List<MeasurementType.Key<*>> = listOf(
        MeasurementType.WEIGHT,
        MeasurementType.BODY_FAT,
        MeasurementType.LBM,
        MeasurementType.HIPS,
        MeasurementType.WAIST,
        MeasurementType.CHEST,
    )

    /**
     * @param measurements      Full measurement history of one user, unsorted.
     * @param timestamp         Timestamp of the measurement to summarize.
     * @param referenceMillis   Configured reference date, or null when unset.
     * @return null while the measurement is not (yet) in [measurements].
     */
    fun compute(
        measurements: List<MeasurementWithValues>,
        types: List<MeasurementType>,
        timestamp: Long,
        referenceMillis: Long?,
    ): MeasurementChangeSummary? {
        val current = measurements.firstOrNull { it.measurement.timestamp == timestamp } ?: return null
        val summaryTypes = SUMMARY_KEYS.mapNotNull { key -> types.firstOrNull { it.key == key } }

        val earlier = measurements
            .filter { it.measurement.timestamp < timestamp }
            .sortedBy { it.measurement.timestamp }

        // Per type, the latest earlier reading carrying it, so a type skipped last time still compares.
        val sincePrevious = ChangeComparison(
            baselineTimestamp = earlier.lastOrNull()?.measurement?.timestamp,
            changes = summaryTypes.mapNotNull { type ->
                val value = numericValueFor(current, type) ?: return@mapNotNull null
                val baseline = earlier.lastOrNull { numericValueFor(it, type) != null }
                change(type, value, baseline)
            },
        )

        // Per type, the reading nearest to the reference date, like a goal's start point.
        val sinceReference = referenceMillis?.let { anchor ->
            val others = measurements.filter { it.measurement.timestamp != timestamp }
            ChangeComparison(
                baselineTimestamp = others.minByOrNull { abs(it.measurement.timestamp - anchor) }
                    ?.measurement?.timestamp,
                changes = summaryTypes.mapNotNull { type ->
                    val value = numericValueFor(current, type) ?: return@mapNotNull null
                    val baseline = others
                        .filter { numericValueFor(it, type) != null }
                        .minByOrNull { abs(it.measurement.timestamp - anchor) }
                    change(type, value, baseline)
                },
            )
        }

        return MeasurementChangeSummary(timestamp, sincePrevious, sinceReference)
    }

    /**
     * Progress since [referenceMillis]: per type, its latest reading against the reading nearest
     * to the reference date. Null when there are no measurements at all.
     */
    fun computeProgress(
        measurements: List<MeasurementWithValues>,
        types: List<MeasurementType>,
        referenceMillis: Long,
    ): ChangeComparison? {
        if (measurements.isEmpty()) return null
        val summaryTypes = SUMMARY_KEYS.mapNotNull { key -> types.firstOrNull { it.key == key } }
        val sorted = measurements.sortedBy { it.measurement.timestamp }
        val latestTimestamp = sorted.last().measurement.timestamp

        return ChangeComparison(
            baselineTimestamp = sorted
                .filter { it.measurement.timestamp != latestTimestamp }
                .minByOrNull { abs(it.measurement.timestamp - referenceMillis) }
                ?.measurement?.timestamp,
            changes = summaryTypes.mapNotNull { type ->
                val withType = sorted.filter { numericValueFor(it, type) != null }
                val latest = withType.lastOrNull() ?: return@mapNotNull null
                val baseline = withType
                    .filter { it !== latest }
                    .minByOrNull { abs(it.measurement.timestamp - referenceMillis) }
                change(type, numericValueFor(latest, type)!!, baseline)
            },
        )
    }

    private fun change(type: MeasurementType, value: Float, baseline: MeasurementWithValues?) =
        MeasurementChange(
            type = type,
            currentValue = value,
            baselineValue = baseline?.let { numericValueFor(it, type) },
            baselineTimestamp = baseline?.measurement?.timestamp,
        )

    private fun numericValueFor(mwv: MeasurementWithValues, type: MeasurementType): Float? =
        mwv.values.firstOrNull { it.type.id == type.id }?.let { vt ->
            when (type.inputType) {
                InputFieldType.FLOAT -> vt.value.floatValue
                InputFieldType.INT   -> vt.value.intValue?.toFloat()
                else                 -> null
            }
        }
}
