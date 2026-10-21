package com.example.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical telemetry metric snapshot persisted locally in Room.
 * Indexed by serverId and timestamp for fast range queries and ring-buffer pruning.
 */
@Entity(
    tableName = "telemetry_snapshots",
    indices = [
        Index(value = ["serverId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class TelemetrySnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cpuUsage: Double,
    val memoryUsage: Double,
    val diskUsage: Double,
    val networkRxRate: Long = 0L,
    val networkTxRate: Long = 0L
)
