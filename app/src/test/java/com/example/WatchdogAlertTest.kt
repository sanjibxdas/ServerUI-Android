package com.example

import com.example.data.model.ServerAlertConfig
import com.example.data.worker.AlertNotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WatchdogAlertTest {

    @Test
    fun testMuteWindowDisabled() {
        val config = ServerAlertConfig(
            muteWindowEnabled = false,
            muteStartHour = 22,
            muteStartMinute = 0,
            muteEndHour = 7,
            muteEndMinute = 0
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        assertFalse(AlertNotificationManager.isWithinMuteWindow(config, cal))
    }

    @Test
    fun testOvernightMuteWindow() {
        val config = ServerAlertConfig(
            muteWindowEnabled = true,
            muteStartHour = 22,
            muteStartMinute = 0,
            muteEndHour = 7,
            muteEndMinute = 0
        )

        // 23:30 should be muted
        val lateNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(AlertNotificationManager.isWithinMuteWindow(config, lateNight))

        // 03:15 should be muted
        val earlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 15)
        }
        assertTrue(AlertNotificationManager.isWithinMuteWindow(config, earlyMorning))

        // 14:00 should NOT be muted
        val afternoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(AlertNotificationManager.isWithinMuteWindow(config, afternoon))
    }

    @Test
    fun testDaytimeMuteWindow() {
        val config = ServerAlertConfig(
            muteWindowEnabled = true,
            muteStartHour = 13,
            muteStartMinute = 0,
            muteEndHour = 15,
            muteEndMinute = 30
        )

        val inside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 10)
        }
        assertTrue(AlertNotificationManager.isWithinMuteWindow(config, inside))

        val outside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(AlertNotificationManager.isWithinMuteWindow(config, outside))
    }
}
