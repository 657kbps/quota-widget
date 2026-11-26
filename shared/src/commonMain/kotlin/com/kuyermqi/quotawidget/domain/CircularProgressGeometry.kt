package com.kuyermqi.quotawidget.domain

import kotlin.math.PI
import kotlin.math.roundToInt

private const val FULL_CIRCLE_DEG = 360f
private const val RING_START_DEG = -90f
private const val GAUGE_START_DEG = 135f
private const val GAUGE_SWEEP_DEG = 270f
private const val RING_SEGMENT_COUNT = 8
private const val GAUGE_SEGMENT_COUNT = 6
private const val GAP_STROKE_RATIO = 0.42f
private const val MIN_SEGMENT_SWEEP_DEG = 1.5f
private const val RAD_TO_DEG = (180.0 / PI).toFloat()

data class CircularSegmentArc(
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    val active: Boolean,
)

sealed interface CircularArcSpec {
    data class Continuous(
        val startAngleDeg: Float,
        val trackSweepDeg: Float,
        val progressSweepDeg: Float,
    ) : CircularArcSpec

    data class Segmented(
        val segments: List<CircularSegmentArc>,
    ) : CircularArcSpec
}

/**
 * Computes arc geometry for circular [UsageProgressStyle] variants so Glance bitmap drawing and
 * Compose preview Canvas render identical rings, gauges, and segmented ticks.
 */
fun buildCircularArcSpec(
    style: UsageProgressStyle,
    fillFraction: Float,
    diameterPx: Float,
    strokePx: Float,
): CircularArcSpec {
    val progress = fillFraction.coerceIn(0f, 1f)
    val startDeg = if (style.isGauge) GAUGE_START_DEG else RING_START_DEG
    val totalSweepDeg = if (style.isGauge) GAUGE_SWEEP_DEG else FULL_CIRCLE_DEG

    if (!style.isSegmented) {
        return CircularArcSpec.Continuous(
            startAngleDeg = startDeg,
            trackSweepDeg = totalSweepDeg,
            progressSweepDeg = totalSweepDeg * progress,
        )
    }

    val radius = ((diameterPx - strokePx) / 2f).coerceAtLeast(1f)
    val capOverhangDeg = (strokePx / radius) * RAD_TO_DEG
    val desiredAirGapDeg = ((strokePx * GAP_STROKE_RATIO) / radius) * RAD_TO_DEG
    val gapDeg = capOverhangDeg + desiredAirGapDeg

    val segmentCount = if (style.isGauge) GAUGE_SEGMENT_COUNT else RING_SEGMENT_COUNT
    val activeCount = activeSegmentCount(progress, segmentCount)

    val segments = if (style.isGauge) {
        val rawSweep = (totalSweepDeg - (segmentCount - 1) * gapDeg) / segmentCount
        val arcSweep = rawSweep.coerceAtLeast(MIN_SEGMENT_SWEEP_DEG)
        val pitch = (totalSweepDeg - arcSweep) / (segmentCount - 1)
        List(segmentCount) { index ->
            CircularSegmentArc(
                startAngleDeg = startDeg + index * pitch,
                sweepAngleDeg = arcSweep,
                active = index < activeCount,
            )
        }
    } else {
        val pitch = FULL_CIRCLE_DEG / segmentCount
        val arcSweep = (pitch - gapDeg).coerceAtLeast(MIN_SEGMENT_SWEEP_DEG)
        val effectiveGap = pitch - arcSweep
        List(segmentCount) { index ->
            CircularSegmentArc(
                startAngleDeg = startDeg + index * pitch + effectiveGap / 2f,
                sweepAngleDeg = arcSweep,
                active = index < activeCount,
            )
        }
    }
    return CircularArcSpec.Segmented(segments = segments)
}

internal fun activeSegmentCount(progress: Float, segmentCount: Int): Int = when {
    progress <= 0f -> 0
    progress >= 1f -> segmentCount
    else -> (progress * segmentCount).roundToInt().coerceIn(1, segmentCount - 1)
}
