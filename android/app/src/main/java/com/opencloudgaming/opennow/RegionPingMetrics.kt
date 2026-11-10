package com.opencloudgaming.opennow

import kotlin.math.abs
import kotlin.math.roundToLong

internal const val REGION_PING_CACHE_TTL_MS = 180_000L

internal data class RegionPingSummary(
    val pingMs: Long,
    val variationMs: Long?,
)

/** Summarizes endpoint TCP-connect samples, not ICMP or live-stream packet loss. */
internal fun summarizeRegionPingSamples(samples: List<Long>): RegionPingSummary? {
    val valid = samples.filter { it >= 0L }
    if (valid.isEmpty()) return null

    val sorted = valid.sorted()
    val middle = sorted.size / 2
    val median = if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        ((sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0).roundToLong()
    }
    val variation = valid
        .zipWithNext { left, right -> abs(left - right) }
        .takeIf(List<Long>::isNotEmpty)
        ?.average()
        ?.roundToLong()

    return RegionPingSummary(median, variation)
}

internal data class StreamRegionPickerEntry(
    val region: StreamRegion,
    val label: String,
)

/** Auto remains separate; measured endpoints sort before routes that have not been probed yet. */
internal fun streamRegionPickerEntries(regions: List<StreamRegion>): List<StreamRegionPickerEntry> {
    val unique = regions.distinctBy(StreamRegion::url)
    val duplicateNames = unique.groupingBy(StreamRegion::name).eachCount()
    return unique
        .sortedWith(
            compareBy<StreamRegion> { it.pingMs ?: Long.MAX_VALUE }
                .thenBy(StreamRegion::name)
                .thenBy(StreamRegion::url),
        )
        .map { region ->
            val host = region.url.substringAfter("://", region.url).substringBefore('/')
            val displayName = if ((duplicateNames[region.name] ?: 0) > 1) {
                "${region.name} ($host)"
            } else {
                region.name
            }
            val label = buildString {
                append(displayName)
                region.pingMs?.let { append(" · TCP ").append(it).append(" ms") }
                region.pingVariationMs?.let { append(" · Δ ").append(it).append(" ms") }
            }
            StreamRegionPickerEntry(region, label)
        }
}
