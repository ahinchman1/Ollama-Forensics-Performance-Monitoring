package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import org.junit.Rule
import org.junit.Test

class TelemetryChartsTest {

    @Rule
    @JvmField
    val composeTestRule = createComposeRule()

    @Test
    fun timeSeriesChart_showsPlaceholder_whenDataIsEmpty() {
        val emptySnapshots = emptyList<CpuTimeSeriesSnapshot>()

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = emptySnapshots)
            }
        }

        composeTestRule
            .onNodeWithText("Awaiting pipeline telemetry execution...")
            .assertIsDisplayed()
    }

    @Test
    fun cpuTimeSeriesChart_sustanedStall_displaysStableAlert() {
        val stalledData = listOf(
            CpuTimeSeriesSnapshot(timestampMillis = 1000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 2000L, cpuConsumption = 5.0, aggregateCpuConsumption = 5.0, temperature = 42, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 3000L, cpuConsumption = 5.0, aggregateCpuConsumption = 5.0, temperature = 42, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 4000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = stalledData)
            }
        }

        composeTestRule.onNodeWithText("CPU Consumption Over Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bound Limits: 5.0% - 90.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stall: 66.7% of time · 1 episode(s) (Stable Execution)").assertIsDisplayed()
    }

    @Test
    fun cpuTimeSeriesChart_intermittentStall_displaysVolatileAlert() {
        // >8s run with a 20% duty cycle and only 1 drop: valid VOLATILE,
        // not coerced to STABLE by the fast-job rule.
        val intermittentData = listOf(
            CpuTimeSeriesSnapshot(timestampMillis = 1000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 3000L, cpuConsumption = 5.0, aggregateCpuConsumption = 5.0, temperature = 42, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 5000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 7000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 9000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 11000L, cpuConsumption = 90.0, aggregateCpuConsumption = 90.0, temperature = 65, threadCount = 8)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = intermittentData)
            }
        }

        composeTestRule.onNodeWithText("CPU Consumption Over Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bound Limits: 5.0% - 90.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stall: 20.0% of time · 1 episode(s) (High Volatility)").assertIsDisplayed()
    }

    @Test
    fun cpuTimeSeriesChart_stableExecution_displaysGreenAlert() {
        val stableData = listOf(
            CpuTimeSeriesSnapshot(timestampMillis = 1000L, cpuConsumption = 60.0, aggregateCpuConsumption = 60.0, temperature = 55, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 2000L, cpuConsumption = 65.0, aggregateCpuConsumption = 65.0, temperature = 56, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 3000L, cpuConsumption = 62.0, aggregateCpuConsumption = 62.0, temperature = 55, threadCount = 4)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = stableData)
            }
        }

        composeTestRule.onNodeWithText("CPU Consumption Over Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bound Limits: 60.0% - 65.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stall: 0.0% of time · 0 episode(s) (Stable Execution)").assertIsDisplayed()
    }
}
