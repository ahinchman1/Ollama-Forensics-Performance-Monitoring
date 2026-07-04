package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BtopMetricsCollectorTest {

    private val collector = BtopMetricsCollector()

@Test
    fun testSuccessfulBtopParsing() {
        // GIVEN
        val sampleBtopOutput = """
        ╭─┐¹cpu┌──┐menu┌┐preset *┌──────────┐14:55:12┌────┐BAT▼ 93% 05:54┌┐- 2000ms +┌─╮
        │                          ⢀  ⢀      ╭─┐i9-9980HK┌────────────────────────┐2.4┌╮│
        │⣶⣴⣤⣧⣦⣴⣤⣦⣦⣴⣾⣶⣶⣶⣶⣶⣾⣾⣶⣴⣴⣷⣶⣶⣶⣶⣿⣿⣶⣿⣿⣷⣶⣶⣷│CPU ■■■■■■■■■■■■■■■■■■■■  54% ⣶⣶⣶⣶⣶  78°C││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C0  96%  81°C│C2  97%  82°C│C4  97%  82°C││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C1   8%  81°C│C3   9%  82°C│C5   9%  82°C││
        │⠿⠻⠛⡟⠟⠻⠛⠟⠟⠻⢿⠿⠿⠿⠿⠿⢿⢿⠿⠻⠻⡿⠿⠿⠿⠿⣿⣿⠿⣿⣿⡿⠿⠿⡿│                Load avg: 10.08 6.96 5.47││
        ├─────────────────├─Preboot───465G─┤│    Pid: Program  User:  MemB        Cpu% ↑│
        │                 │ IO ⣀⣀⣀⣀⣀⣀⣀⣀⣀⣀  ││   27236 llama-s aman+  2.5G ⣤⣤⣤⣤⣤ 47.5  █│
        ╰─────────────────┴────────────────╯│    3292 Google  aman+  119M ⢀⣀⣀⣀⣀  0.0  ↓│
        ╰┘↑ select ↓└┘info ↵└┘signals└┘N────┘0/758└╯
        """.trimIndent()

        // WHEN
        val result = collector.parseBtopDataFromString(sampleBtopOutput)

        // THEN
        assertTrue(result is Result.Success, "Expected parser to return Result.Success but encountered a Failure wrapper")
        val metrics = result.data

        assertEquals(78, metrics.temperature, "Global temperature extraction failed")
        assertEquals(0L, metrics.processCpuConsumption, "ps-based CPU should be 0 when ollama is not running in test env")
        assertEquals(54.0, metrics.btopProcessCpuConsumption, "btop-normalized CPU parsing from output failed")
        assertTrue(metrics.threadCount >= 0, "Thread count should be a valid non-negative value")
        assertEquals(6, metrics.cores.size, "Failed to capture all 6 distinct cores present in text rows")
        assertEquals(316.0, metrics.aggregateCpuConsumption, "Aggregate CPU consumption should sum all core percentages (96+97+97+8+9+9 = 316)")

        val sortedCores = metrics.cores
        assertEquals("Core 0", sortedCores.first().name)
        assertEquals(81, sortedCores.first().temperature)
    }

    @Test
    fun testGlobalTemperatureFallbackCalculation() {
        // GIVEN
        val corruptedCpuOutput = """
        │⣶⣴⣤⣧⣦⣴⣤⣦⣦⣴⣾⣶⣶⣶⣶⣶⣾⣾⣶⣴⣴⣷⣶⣶⣶⣶⣿⣿⣶⣿⣿⣷⣶⣶⣷│CPU UNKNOWN CONTENT OR SPACING ERROR││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C0  50%  70°C│C1  50%  80°C││
        ╰┘↑ select ↓└┘info ↵└┘signals└┘N────┘0/120└╯
        """.trimIndent()

        // WHEN
        val result = collector.parseBtopDataFromString(corruptedCpuOutput)

        // THEN
        assertTrue(result is Result.Success)
        val metrics = result.data

        // The fallback calculation should average out Cores 0 and 1 ( (70 + 80) / 2 = 75 )
        assertEquals(75, metrics.temperature, "Fallback calculation failed to properly average available cores")
    }

    @Test
    fun testEmptyOrMissingDataGracefulDegradation() {
        // GIVEN
        val boundaryOutput = "Pane error or empty buffer response text"

        // WHEN
        val result = collector.parseBtopDataFromString(boundaryOutput)

        // THEN
        assertTrue(result is Result.Success)
        val metrics = result.data

        assertTrue(metrics.cores.isEmpty(), "Cores list should be empty on bad input data")
        assertEquals(0, metrics.temperature, "Temperature should fall back cleanly to 0")
        assertEquals(0L, metrics.processCpuConsumption, "Process tracking should fall back cleanly to 0")
        assertEquals(0.0, metrics.btopProcessCpuConsumption, "btop CPU should be 0 on bad input data")
        assertTrue(metrics.threadCount >= 0, "Threads should be a valid non-negative value when Ollama may be running on host")
    }
}