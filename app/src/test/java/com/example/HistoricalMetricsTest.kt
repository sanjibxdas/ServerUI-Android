package com.example

import com.example.data.local.db.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalMetricsTest {

    @Test
    fun testTelemetrySnapshotModel() {
        val now = System.currentTimeMillis()
        val snapshot = TelemetrySnapshot(
            id = 1L,
            serverId = "srv_alpha",
            timestamp = now,
            cpuUsage = 42.5,
            memoryUsage = 68.2,
            diskUsage = 55.0,
            networkRxRate = 1048576L,
            networkTxRate = 524288L
        )

        assertEquals("srv_alpha", snapshot.serverId)
        assertEquals(42.5, snapshot.cpuUsage, 0.001)
        assertEquals(68.2, snapshot.memoryUsage, 0.001)
        assertEquals(55.0, snapshot.diskUsage, 0.001)
        assertEquals(1048576L, snapshot.networkRxRate)
        assertEquals(524288L, snapshot.networkTxRate)
    }

    @Test
    fun testWindowCalculations() {
        val now = 1700000000000L
        val oneHourAgo = now - 3600 * 1000L
        val oneDayAgo = now - 24 * 3600 * 1000L
        val sevenDaysAgo = now - 7 * 24 * 3600 * 1000L

        assertTrue(now > oneHourAgo)
        assertTrue(oneHourAgo > oneDayAgo)
        assertTrue(oneDayAgo > sevenDaysAgo)

        assertEquals(3600000L, now - oneHourAgo)
        assertEquals(86400000L, now - oneDayAgo)
        assertEquals(604800000L, now - sevenDaysAgo)
    }

    @Test
    fun testRingBufferCapSimulation() {
        val cap = 5
        val list = mutableListOf<TelemetrySnapshot>()
        for (i in 1..10) {
            list.add(
                TelemetrySnapshot(
                    id = i.toLong(),
                    serverId = "srv_test",
                    timestamp = 1000L * i,
                    cpuUsage = i * 10.0,
                    memoryUsage = 50.0,
                    diskUsage = 40.0
                )
            )
            // Emulate ring-buffer capping to newest `cap` items
            if (list.size > cap) {
                list.removeAt(0)
            }
        }

        assertEquals(cap, list.size)
        assertEquals(6L, list.first().id)
        assertEquals(10L, list.last().id)
        assertEquals(100.0, list.last().cpuUsage, 0.001)
    }
}
