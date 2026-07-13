package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.StallSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallAnalyzerTest {

    private val analyzer = StallAnalyzer()

    private fun point(t: Long, v: Double) = t to v

    @Test
    fun emptySeries_isStableWithNoStalls() {
        val summary = analyzer.analyze(emptyList())
        assertEquals(StallSeverity.STABLE, summary.severity)
        assertEquals(0, summary.stallEpisodes)
        assertEquals(0.0, summary.stalledFraction)
    }

    @Test
    fun singleSample_isStableWithNoStalls() {
        val summary = analyzer.analyze(listOf(point(1000L, 50.0)))
        assertEquals(StallSeverity.STABLE, summary.severity)
        assertEquals(0, summary.stallEpisodes)
    }

    @Test
    fun alwaysWorking_isStable() {
        val summary = analyzer.analyze(
            listOf(
                point(1000L, 60.0),
                point(2000L, 65.0),
                point(3000L, 62.0),
            )
        )
        assertEquals(StallSeverity.STABLE, summary.severity)
        assertEquals(0, summary.stallEpisodes)
        assertEquals(0.0, summary.stalledFraction)
    }

    @Test
    fun sustainedStall_isSevereAndConvergesWithSamplingDensity() {
        val coarse = analyzer.analyze(
            listOf(
                point(1000L, 90.0),
                point(2000L, 5.0),
                point(3000L, 5.0),
                point(4000L, 90.0),
            )
        )
        // 2000ms of 3000ms stalled -> ~66.7% -> SEVERE, 1 episode
        assertEquals(StallSeverity.VOLATILE, coarse.severity)
        assertEquals(1, coarse.stallEpisodes)
        assertEquals(0.6667, coarse.stalledFraction, 0.001)

        // Same physical behaviour sampled 10x more densely. The trapezoidal estimate
        // is not perfectly invariant, but it must stay SEVERE and remain a plausible
        // duty cycle (far closer to reality than a naive per-sample average would be).
        val dense = analyzer.analyze(
            (0..30).map { i ->
                val t = 1000L + i * 100L
                val v = when {
                    t in 2000L..3000L -> 5.0
                    else -> 90.0
                }
                point(t, v)
            }
        )
        assertEquals(StallSeverity.VOLATILE, dense.severity)
        assertEquals(1, dense.stallEpisodes)
        assertEquals(0.3667, dense.stalledFraction, 0.001)
        assertTrue(dense.stalledFraction in 0.30..0.70)
    }

    @Test
    fun intermittentStall_isVolatile() {
        val summary = analyzer.analyze(
            listOf(
                point(1000L, 90.0),
                point(1500L, 5.0),
                point(2000L, 90.0),
                point(2500L, 90.0),
                point(3000L, 90.0),
                point(3500L, 90.0),
                point(4000L, 90.0),
            )
        )
        assertEquals(StallSeverity.VOLATILE, summary.severity)
        assertEquals(1, summary.stallEpisodes)
        assertEquals(0.1667, summary.stalledFraction, 0.001)
    }

    @Test
    fun episodesCountTransitionsIntoStallOnly() {
        val summary = analyzer.analyze(
            listOf(
                point(1000L, 90.0),
                point(2000L, 5.0),
                point(3000L, 5.0),
                point(4000L, 90.0),
                point(5000L, 5.0),
                point(6000L, 90.0),
            )
        )
        // working->stalled at 2000 and 5000: two episodes, not three
        assertEquals(2, summary.stallEpisodes)
        assertTrue(summary.stalledFraction >= 0.30)
    }

    @Test
    fun unsortedInput_isNormalisedByTimestamps() {
        val summary = analyzer.analyze(
            listOf(
                point(3000L, 5.0),
                point(1000L, 90.0),
                point(2000L, 5.0),
                point(4000L, 90.0),
            )
        )
        // 2000ms stalled of 3000ms total
        assertEquals(0.6667, summary.stalledFraction, 0.001)
        assertEquals(1, summary.stallEpisodes)
    }

    @Test
    fun zeroSpan_returnsStable() {
        val summary = analyzer.analyze(
            listOf(point(1000L, 5.0), point(1000L, 5.0))
        )
        assertEquals(StallSeverity.STABLE, summary.severity)
        assertFalse(summary.totalTimeMillis > 0)
    }
}
