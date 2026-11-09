package com.opencloudgaming.opennow

import java.util.Locale

internal data class StreamNetworkWarning(
    val key: String,
    val message: String,
)

/**
 * Turns only direct network measurements into an in-stream warning. Decoder FPS is deliberately
 * excluded as a trigger because a slow device is not evidence of a bad connection.
 */
internal fun streamNetworkWarning(
    stats: StreamRuntimeStats,
): StreamNetworkWarning? {
    val reasons = buildList {
        stats.pingMs
            ?.takeIf { it >= 0 && StreamQuality.latency(it) == StreamQualityLevel.Poor }
            ?.let { add("latency" to "$it ms latency") }

        val packetDeltaIsUsable = stats.packetsLostDelta != null &&
            stats.packetsReceivedDelta != null &&
            stats.packetsLostDelta >= 0L &&
            stats.packetsReceivedDelta >= 0L &&
            stats.packetsLostDelta + stats.packetsReceivedDelta > 0L
        stats.packetLossPct
            ?.takeIf {
                packetDeltaIsUsable &&
                    it >= 0.0 &&
                    StreamQuality.packetLoss(it) == StreamQualityLevel.Poor
            }
            ?.let { add("loss" to "${"%.2f".format(Locale.US, it)}% packet loss") }

        stats.jitterMs
            ?.takeIf { it >= 0.0 && StreamQuality.jitter(it) == StreamQualityLevel.Poor }
            ?.let { add("jitter" to "${"%.1f".format(Locale.US, it)} ms jitter") }
    }
    if (reasons.isEmpty()) return null

    val measured = reasons.map { it.second }
    return StreamNetworkWarning(
        key = reasons.map { it.first }.sorted().joinToString("+"),
        message = buildString {
            append(measured.joinToString(" · "))
            append(". You may experience lag due to your internet connection.")
        },
    )
}

/** Requires a sustained problem and shows at most one connection banner per stream session. */
/**
 * Separates device-side decode pressure from network quality. Network measurements take
 * priority so a local decoder slowdown is not incorrectly blamed on the internet connection.
 */
internal fun streamPerformanceWarning(stats: StreamRuntimeStats): StreamNetworkWarning? {
    streamNetworkWarning(stats)?.let { return it }

    val receivedFps = stats.receivedFps?.takeIf { it >= 50 } ?: return null
    val decodedFps = stats.decodedFps?.takeIf { it >= 0 } ?: return null
    val decodeMs = stats.decodeMs?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val frameBudgetMs = 1_000.0 / receivedFps.toDouble()
    val decoderIsFallingBehind = decodedFps <= receivedFps * 0.75 &&
        decodeMs >= maxOf(12.0, frameBudgetMs * 0.85)
    if (!decoderIsFallingBehind) return null

    return StreamNetworkWarning(
        key = "device_video",
        message = "$decodedFps FPS decoded from $receivedFps FPS received " +
            "($decodeMs ms decode). The device video path is falling behind; " +
            "try 720p/60 FPS, H.264, and turn off sharpening.",
    )
}

internal class StreamNetworkWarningGate(
    private val minimumConsecutiveSamples: Int = 3,
) {
    private var candidateKey: String? = null
    private var consecutiveSamples = 0
    private var warningShown = false

    init {
        require(minimumConsecutiveSamples > 0)
    }

    fun update(candidate: StreamNetworkWarning?): StreamNetworkWarning? {
        if (candidate == null) {
            candidateKey = null
            consecutiveSamples = 0
            return null
        }

        if (candidate.key == candidateKey) {
            consecutiveSamples += 1
        } else {
            candidateKey = candidate.key
            consecutiveSamples = 1
        }
        if (consecutiveSamples < minimumConsecutiveSamples) return null
        if (warningShown) return null
        warningShown = true
        return candidate
    }
}
