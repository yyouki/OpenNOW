package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionPingMetricsTest {

    @Test
    fun medianIgnoresSingleSlowOutlierAndReportsSampleVariation() {
        assertEquals(
            RegionPingSummary(pingMs = 22, variationMs = 30),
            summarizeRegionPingSamples(listOf(20, 22, 80)),
        )
    }

    @Test
    fun evenSampleCountUsesMiddlePairAverage() {
        assertEquals(
            RegionPingSummary(pingMs = 15, variationMs = 10),
            summarizeRegionPingSamples(listOf(10, 20)),
        )
    }

    @Test
    fun invalidAndMissingSamplesAreIgnored() {
        assertEquals(
            RegionPingSummary(pingMs = 31, variationMs = 2),
            summarizeRegionPingSamples(listOf(-1, 30, 32)),
        )
        assertNull(summarizeRegionPingSamples(emptyList()))
        assertNull(summarizeRegionPingSamples(listOf(-1, -5)))
    }

    @Test
    fun routeChoicesSortMeasuredEndpointsAndDisambiguateDuplicateNames() {
        val entries = streamRegionPickerEntries(
            listOf(
                StreamRegion("Tokyo", "https://tokyo.example", 45, 5),
                StreamRegion("US West", "https://us-west.example", 110),
                StreamRegion("Tokyo", "https://tokyo-backup.example", 65),
                StreamRegion("Europe", "https://europe.example"),
            ),
        )
        assertEquals(
            listOf(
                "Tokyo (tokyo.example) · TCP 45 ms · Δ 5 ms",
                "Tokyo (tokyo-backup.example) · TCP 65 ms",
                "US West · TCP 110 ms",
                "Europe",
            ),
            entries.map(StreamRegionPickerEntry::label),
        )
        assertEquals("https://tokyo.example", entries.first().region.url)
    }
}
