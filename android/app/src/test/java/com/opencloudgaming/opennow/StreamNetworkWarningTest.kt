package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamNetworkWarningTest {
    @Test
    fun warnsFromMeasuredPacketDeltasWithConciseInternetMessage() {
        val warning = streamNetworkWarning(
            stats = StreamRuntimeStats(
                receivedFps = 70,
                packetLossPct = 3.25,
                packetsLostDelta = 13,
                packetsReceivedDelta = 387,
            ),
        )

        requireNotNull(warning)
        assertEquals("loss", warning.key)
        assertEquals(
            "3.25% packet loss. You may experience lag due to your internet connection.",
            warning.message,
        )
    }

    @Test
    fun ignoresLossPercentageWithoutUsableCounterDelta() {
        assertNull(
            streamNetworkWarning(
                stats = StreamRuntimeStats(packetLossPct = 9.0),
            ),
        )
    }

    @Test
    fun latencyUsesTheSameConciseInternetMessage() {
        val warning = streamNetworkWarning(
            stats = StreamRuntimeStats(pingMs = 150),
        )

        requireNotNull(warning)
        assertEquals(
            "150 ms latency. You may experience lag due to your internet connection.",
            warning.message,
        )
    }

    @Test
    fun decoderOnlySlowdownDoesNotBlameTheConnection() {
        assertNull(
            streamNetworkWarning(
                stats = StreamRuntimeStats(receivedFps = 120, decodedFps = 30, decodeMs = 35.0),
            ),
        )
    }

    @Test
    fun decoderBacklogGetsDeviceAdviceInsteadOfAnInternetWarning() {
        val stats = StreamRuntimeStats(
            receivedFps = 60,
            decodedFps = 40,
            decodeMs = 18.0,
        )

        requireNotNull(streamPerformanceWarning(stats)).let { warning ->
            assertEquals("device_video", warning.key)
            assertTrue(warning.message.contains("device video path is falling behind"))
            assertTrue(warning.message.contains("720p/60 FPS"))
        }
        assertNull(streamNetworkWarning(stats))
    }

    @Test
    fun networkWarningKeepsPriorityOverDecoderAdvice() {
        val stats = StreamRuntimeStats(
            pingMs = 150,
            receivedFps = 60,
            decodedFps = 35,
            decodeMs = 20.0,
        )

        assertEquals("latency", streamPerformanceWarning(stats)?.key)
    }

    @Test
    fun gameLimitedIncomingFrameRateDoesNotTriggerDecoderAdvice() {
        assertNull(
            streamPerformanceWarning(
                StreamRuntimeStats(receivedFps = 30, decodedFps = 30, decodeMs = 28.0),
            ),
        )
    }

    @Test
    fun sustainedPoorMeasurementsShowOnlyOncePerStreamSession() {
        val gate = StreamNetworkWarningGate(minimumConsecutiveSamples = 3)
        val warning = StreamNetworkWarning("latency", "150 ms latency")

        assertNull(gate.update(warning))
        assertNull(gate.update(warning))
        assertEquals(warning, gate.update(warning))
        assertNull(gate.update(warning))
        assertNull(gate.update(null))
        assertNull(gate.update(warning))
        assertNull(gate.update(warning))
        assertNull(gate.update(warning))
    }

    @Test
    fun healthySampleResetsConsecutiveWarningCount() {
        val gate = StreamNetworkWarningGate(minimumConsecutiveSamples = 2)
        val warning = StreamNetworkWarning("jitter", "40 ms jitter")

        assertNull(gate.update(warning))
        assertNull(gate.update(null))
        assertNull(gate.update(warning))
        assertEquals(warning, gate.update(warning))
    }
}
