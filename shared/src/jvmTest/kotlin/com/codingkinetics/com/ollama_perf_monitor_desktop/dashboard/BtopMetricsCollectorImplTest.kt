package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BtopMetricsCollectorTest {

    private val collector = BtopMetricsCollectorImpl()

    @Test
    fun testSuccessfulBtopParsing() {
        // 1. Arrange: Provide a realistic mockup chunk matching your raw btop sample
        val sampleBtopOutput = """
        ╭─┐¹cpu┌──┐menu┌┐preset *┌──────────┐14:55:12┌────┐BAT▼ 93% 05:54┌┐- 2000ms +┌─╮
        │                          ⢀  ⢀      ╭─┐i9-9980HK┌────────────────────────┐2.4┌╮│
        │⣶⣴⣤⣧⣦⣴⣤⣦⣦⣴⣾⣶⣶⣶⣶⣶⣾⣾⣶⣴⣴⣷⣶⣶⣶⣶⣿⣿⣶⣿⣿⣷⣶⣶⣷│CPU ■■■■■■■■■■■■■■■■■■■■  54% ⣶⣶⣶⣶⣶  78°C││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C0  96%  81°C│C2  97%  82°C│C4  97%  82°C││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C1   8%  81°C│C3   9%  82°C│C5   9%  82°C││
        │⠿⠻⠛⡟⠟⠻⠛⠟⠟⠻⢿⠿⠿⠿⠿⠿⢿⢿⠿⠻⠻⡿⠿⠿⠿⠿⣿⣿⠿⣿⣿⡿⠿⠿⡿│                Load avg: 10.08 6.96 5.47││
        ├─────────────────├─Preboot───465G─┤│    Pid: Program  User:  MemB        Cpu% ↑│
        │                 │ IO ⣀⣀⣀⣀⣀⣀⣀⣀⣀⣀  ││   39408 ollama  aman+  2.4G ⣤⣤⣤⣤⣤ 49.4  █│
        ╰─────────────────┴────────────────╯│    3292 Google  aman+  119M ⢀⣀⣀⣀⣀  0.0  ↓│
        ╰┘↑ select ↓└┘info ↵└┘signals└┘N────┘0/758└╯
        """.trimIndent()

        // 2. Act: Execute the parsing layer
        val metrics = collector.parseBtopDataFromString(sampleBtopOutput)

        // 3. Assert: Verify the domain expectations match perfectly
        assertEquals(78, metrics.temperature, "Global temperature extraction failed")
        assertEquals(49L, metrics.processCpuConsumption, "Ollama standalone CPU consumption parsing failed")
        assertEquals(758, metrics.threadCount, "Fallback bottom thread counter tracking failed")

        // Assert core populations
        assertEquals(6, metrics.cores.size, "Failed to capture all 6 distinct cores present in text rows")
        assertEquals("Core 0", metrics.cores.first().name)
        assertEquals(81, metrics.cores.first().temperature)
    }

    @Test
    fun testGlobalTemperatureFallbackCalculation() {
        // Arrange: A bad block where the global "CPU" row is missing temperature data entirely
        val corruptedCpuOutput = """
        │⣶⣴⣤⣧⣦⣴⣤⣦⣦⣴⣾⣶⣶⣶⣶⣶⣾⣾⣶⣴⣴⣷⣶⣶⣶⣶⣿⣿⣶⣿⣿⣷⣶⣶⣷│CPU UNKNOWN CONTENT OR SPACING ERROR││
        │⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿│C0  50%  70°C│C1  50%  80°C││
        ╰┘↑ select ↓└┘info ↵└┘signals└┘N────┘0/120└╯
        """.trimIndent()

        // Act
        val metrics = collector.parseBtopDataFromString(corruptedCpuOutput)

        // Assert: The fallback calculation should average out Cores 0 and 1 ( (70 + 80) / 2 = 75 )
        assertEquals(75, metrics.temperature, "Fallback calculation failed to properly average available cores")
        assertEquals(120, metrics.threadCount)
    }

    @Test
    fun testEmptyOrMissingDataGracefulDegradation() {
        // Arrange: Completly blank window returns or error buffers
        val boundaryOutput = "Pane error or empty buffer response text"

        // Act
        val metrics = collector.parseBtopDataFromString(boundaryOutput)

        // Assert: Ensure no out-of-bounds array splits crash your process
        assertTrue(metrics.cores.isEmpty(), "Cores list should be empty on bad input data")
        assertEquals(0, metrics.temperature, "Temperature should fall back cleanly to 0")
        assertEquals(0L, metrics.processCpuConsumption, "Process tracking should fall back cleanly to 0")
        assertEquals(0, metrics.threadCount, "Threads should fall back cleanly to 0")
    }
}