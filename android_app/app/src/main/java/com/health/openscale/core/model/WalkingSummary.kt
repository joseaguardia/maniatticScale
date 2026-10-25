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
package com.health.openscale.core.model

/** Totals of the walking sessions read from Health Connect over the last [days] days. */
data class WalkingSummary(
    val days: Int,
    val walkCount: Int,
    val totalDistanceMeters: Double,
    val totalDurationMinutes: Long,
    val totalKilocalories: Double,
)

/** What the post-save summary and the overview can show about walking activity. */
sealed interface WalkingSummaryState {
    /** Health Connect is not installed or not supported on this device. */
    data object Unavailable : WalkingSummaryState
    data object PermissionRequired : WalkingSummaryState
    data object Error : WalkingSummaryState
    /** One summary per requested period, in the same order. */
    data class Loaded(val summaries: List<WalkingSummary>) : WalkingSummaryState {
        val summary: WalkingSummary get() = summaries.first()
    }
}
