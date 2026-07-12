package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun cpuTimeSeriesChart_calculatesSevereDelta_displaysRedAlert() {
        val highVarianceData = listOf(
            CpuTimeSeriesSnapshot(timestampMillis = 1000L, cpuConsumption = 5.0, aggregateCpuConsumption = 5.0, temperature = 40, threadCount = 4),
            CpuTimeSeriesSnapshot(timestampMillis = 2000L, cpuConsumption = 95.0, aggregateCpuConsumption = 95.0, temperature = 65, threadCount = 8),
            CpuTimeSeriesSnapshot(timestampMillis = 3000L, cpuConsumption = 10.0, aggregateCpuConsumption = 10.0, temperature = 42, threadCount = 4)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = highVarianceData)
            }
        }

        composeTestRule.onNodeWithText("CPU Consumption Over Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bound Limits: 5.0% - 95.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Δ: 87.5% (Severe Thrashing)").assertIsDisplayed()
    }

    @Test
    fun cpuTimeSeriesChart_calculatesTolerableDelta_displaysYellowAlert() {
        val midVarianceData = listOf(
            CpuTimeSeriesSnapshot(timestampMillis = 1000L, cpuConsumption = 40.0, aggregateCpuConsumption = 40.0, temperature = 50, threadCount = 6),
            CpuTimeSeriesSnapshot(timestampMillis = 2000L, cpuConsumption = 56.0, aggregateCpuConsumption = 56.0, temperature = 58, threadCount = 6),
            CpuTimeSeriesSnapshot(timestampMillis = 3000L, cpuConsumption = 48.0, aggregateCpuConsumption = 48.0, temperature = 52, threadCount = 6)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CpuTimeSeriesChart(snapshots = midVarianceData)
            }
        }

        composeTestRule.onNodeWithText("CPU Consumption Over Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bound Limits: 40.0% - 56.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Δ: 12.0% (High Volatility)").assertIsDisplayed()
    }

    @Test
    fun cpuTimeSeriesChart_calculatesFlatDelta_displaysGreenAlert() {
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
        composeTestRule.onNodeWithText("Δ: 4.0% (Stable Execution)").assertIsDisplayed()
    }
}
