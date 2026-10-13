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

    suspend fun walkingSummary(days: Int = SUMMARY_DAYS): WalkingSummaryState {
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
            val start = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant()
            val walks = readWalkingSessions(client, start, Instant.now())

            var distance = 0.0
            var kcal = 0.0
            var minutes = 0L
            walks.forEach { session ->
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
                distance += totals[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                // Prefer active calories; fall back to total ones when the source only writes those.
                val active = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                kcal += if (active > 0.0) active
                else totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                minutes += Duration.between(session.startTime, session.endTime).toMinutes()
            }

            WalkingSummaryState.Loaded(WalkingSummary(days, walks.size, distance, minutes, kcal))
        } catch (e: SecurityException) {
            WalkingSummaryState.PermissionRequired
        } catch (e: Exception) {
            LogManager.e(TAG, "Reading walking sessions from Health Connect failed", e)
            WalkingSummaryState.Error
        }
    }

    // A single page is enough for a couple of weeks of sessions. Paging is avoided on purpose: the
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
        private const val MAX_SESSIONS = 1000
    }
}
