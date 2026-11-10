package com.opencloudgaming.opennow

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamStatusCustomizationTest {

    @Test
    fun timeAndSsidAreOptionalAndPersistIndependently() {
        val defaults = AppSettings()
        assertFalse(StreamStatusItem.Time.enabledIn(defaults))
        assertFalse(StreamStatusItem.Ssid.enabledIn(defaults))

        val withTime = StreamStatusItem.Time.setEnabled(defaults, true)
        val withSsid = StreamStatusItem.Ssid.setEnabled(withTime, true)

        assertTrue(StreamStatusItem.Time.enabledIn(withSsid))
        assertTrue(StreamStatusItem.Ssid.enabledIn(withSsid))
        assertEquals(defaults.streamStatsMetrics.enabledCount() + 2, withSsid.streamStatsMetrics.enabledCount())
        assertFalse(StreamStatusItem.Time.enabledIn(StreamStatusItem.Time.setEnabled(withSsid, false)))
    }

    @Test
    fun clockFormatterUsesDeviceTimeZone() {
        val timestamp = 13L * 60L * 60L * 1000L + 51L * 60L * 1000L
        assertEquals("13:51", formatStatusBarTime(timestamp, Locale.US, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun ssidSanitizationHandlesAndroidHiddenValues() {
        assertEquals("Home Wi-Fi", normalizeWifiSsid("\"Home Wi-Fi\""))
        assertEquals("HomeWiFi", normalizeWifiSsid("Home\u0000WiFi"))
        assertNull(normalizeWifiSsid("<unknown ssid>"))
        assertNull(normalizeWifiSsid("0x"))
        assertNull(normalizeWifiSsid(null))
    }
}
