package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: TelemetrySnapshot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<TelemetrySnapshot>)

    @Query("SELECT * FROM telemetry_snapshots WHERE serverId = :serverId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getSnapshotsForServerSince(serverId: String, sinceTimestamp: Long): Flow<List<TelemetrySnapshot>>

    @Query("SELECT * FROM telemetry_snapshots WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshots(serverId: String, limit: Int): List<TelemetrySnapshot>

    @Query("DELETE FROM telemetry_snapshots WHERE serverId = :serverId AND timestamp < :cutoffTimestamp")
    suspend fun pruneOlderThan(serverId: String, cutoffTimestamp: Long): Int

    /**
     * Enforces ring-buffer capacity by retaining only the newest `maxCount` entries per server.
     */
    @Query("""
        DELETE FROM telemetry_snapshots 
        WHERE serverId = :serverId 
        AND id NOT IN (
            SELECT id FROM telemetry_snapshots 
            WHERE serverId = :serverId 
            ORDER BY timestamp DESC 
            LIMIT :maxCount
        )
    """)
    suspend fun enforceRingBufferCap(serverId: String, maxCount: Int): Int

    @Query("SELECT COUNT(*) FROM telemetry_snapshots WHERE serverId = :serverId")
    suspend fun countSnapshotsForServer(serverId: String): Int

    @Query("DELETE FROM telemetry_snapshots WHERE serverId = :serverId")
    suspend fun deleteAllForServer(serverId: String): Int
}
