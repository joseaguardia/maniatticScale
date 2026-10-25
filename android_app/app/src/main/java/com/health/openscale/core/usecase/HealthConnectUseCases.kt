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
package com.health.openscale.core.usecase

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.health.openscale.core.model.WalkingSummary
import com.health.openscale.core.model.WalkingSummaryState
import com.health.openscale.core.utils.LogManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads walking sessions from Health Connect (written there by Samsung Health) on demand.
 * Nothing is stored in the openScale database.
 */
@Singleton
class HealthConnectUseCases @Inject constructor(
    private val application: Application,
) {
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    /** Walking totals of each period in [periods] (in days, today included), from a single read. */
    suspend fun walkingSummary(periods: List<Int> = listOf(SUMMARY_DAYS)): WalkingSummaryState {
        if (HealthConnectClient.getSdkStatus(application) != HealthConnectClient.SDK_AVAILABLE) {
            return WalkingSummaryState.Unavailable
        }
        return try {
            val client = HealthConnectClient.getOrCreate(application)
            if (!client.permissionController.getGrantedPermissions().containsAll(readPermissions)) {
                return WalkingSummaryState.PermissionRequired
            }

            // Today plus the previous days - 1, starting at local midnight.
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun periodStart(days: Int): Instant = today.minusDays(days - 1L).atStartOfDay(zone).toInstant()

            val walks = readWalkingSessions(client, periodStart(periods.max()), Instant.now())
                .map { session -> sessionTotals(client, session) }

            val summaries = periods.map { days ->
                val start = periodStart(days)
                val inPeriod = walks.filter { !it.start.isBefore(start) }
                WalkingSummary(
                    days = days,
                    walkCount = inPeriod.size,
                    totalDistanceMeters = inPeriod.sumOf { it.distanceMeters },
                    totalDurationMinutes = inPeriod.sumOf { it.minutes },
                    totalKilocalories = inPeriod.sumOf { it.kilocalories },
                )
            }
            WalkingSummaryState.Loaded(summaries)
        } catch (e: SecurityException) {
            WalkingSummaryState.PermissionRequired
        } catch (e: Exception) {
            LogManager.e(TAG, "Reading walking sessions from Health Connect failed", e)
            WalkingSummaryState.Error
        }
    }

    private suspend fun sessionTotals(client: HealthConnectClient, session: ExerciseSessionRecord): SessionTotals {
        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
            )
        )
        // Prefer active calories; fall back to total ones when the source only writes those.
        val active = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        return SessionTotals(
            start = session.startTime,
            distanceMeters = totals[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
            minutes = Duration.between(session.startTime, session.endTime).toMinutes(),
            kilocalories = if (active > 0.0) active
            else totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
        )
    }

    private data class SessionTotals(
        val start: Instant,
        val distanceMeters: Double,
        val minutes: Long,
        val kilocalories: Double,
    )

    // A single page is enough for a month of sessions. Paging is avoided on purpose: the
    // Android 13 provider can hand back a non-null token on the last page, which loops until the
    // read quota is exhausted.
    private suspend fun readWalkingSessions(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<ExerciseSessionRecord> =
        client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = MAX_SESSIONS,
            )
        ).records.filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING }

    companion object {
        private const val TAG = "HealthConnectUseCases"
        const val SUMMARY_DAYS = 14
        val OVERVIEW_PERIODS = listOf(7, 30)
        private const val MAX_SESSIONS = 1000
    }
}
