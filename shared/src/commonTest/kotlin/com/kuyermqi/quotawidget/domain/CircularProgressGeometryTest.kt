package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CircularProgressGeometryTest {
    @Test
    fun continuousRingUsesFullCircleFromTop() {
        val spec = buildCircularArcSpec(
            style = UsageProgressStyle.RING,
            fillFraction = 0.25f,
            diameterPx = 300f,
            strokePx = 24f,
        )
        val continuous = assertIs<CircularArcSpec.Continuous>(spec)
        assertEquals(-90f, continuous.startAngleDeg)
        assertEquals(360f, continuous.trackSweepDeg)
        assertEquals(90f, continuous.progressSweepDeg)
    }

    @Test
    fun continuousGaugeUses270ArcFromBottomLeft() {
        val spec = buildCircularArcSpec(
            style = UsageProgressStyle.GAUGE,
            fillFraction = 0.5f,
            diameterPx = 300f,
            strokePx = 24f,
        )
        val continuous = assertIs<CircularArcSpec.Continuous>(spec)
        assertEquals(135f, continuous.startAngleDeg)
        assertEquals(270f, continuous.trackSweepDeg)
        assertEquals(135f, continuous.progressSweepDeg)
    }

    @Test
    fun segmentedRingAndGaugeActiveCountsRespectBoundaries() {
        val ringEmpty = assertIs<CircularArcSpec.Segmented>(
            buildCircularArcSpec(UsageProgressStyle.RING_SEGMENTED, 0f, 300f, 24f),
        )
        assertEquals(8, ringEmpty.segments.size)
        assertEquals(0, ringEmpty.segments.count { it.active })
        assertTrue(ringEmpty.segments.first().sweepAngleDeg > 25f)

        val ringSmall = assertIs<CircularArcSpec.Segmented>(
            buildCircularArcSpec(UsageProgressStyle.RING_SEGMENTED, 0.01f, 300f, 24f),
        )
        assertEquals(1, ringSmall.segments.count { it.active })

        val ringNearlyFull = assertIs<CircularArcSpec.Segmented>(
            buildCircularArcSpec(UsageProgressStyle.RING_SEGMENTED, 0.99f, 300f, 24f),
        )
        assertEquals(7, ringNearlyFull.segments.count { it.active })

        val ringFull = assertIs<CircularArcSpec.Segmented>(
            buildCircularArcSpec(UsageProgressStyle.RING_SEGMENTED, 1f, 300f, 24f),
        )
        assertEquals(8, ringFull.segments.count { it.active })

        val gaugeHalf = assertIs<CircularArcSpec.Segmented>(
            buildCircularArcSpec(UsageProgressStyle.GAUGE_SEGMENTED, 0.5f, 300f, 24f),
        )
        assertEquals(6, gaugeHalf.segments.size)
        assertEquals(3, gaugeHalf.segments.count { it.active })
        assertTrue(gaugeHalf.segments.first().sweepAngleDeg > 25f)
        assertEquals(135f, gaugeHalf.segments.first().startAngleDeg)
        val last = gaugeHalf.segments.last()
        assertTrue(kotlin.math.abs((last.startAngleDeg + last.sweepAngleDeg) - 405f) < 0.01f)
    }
}
