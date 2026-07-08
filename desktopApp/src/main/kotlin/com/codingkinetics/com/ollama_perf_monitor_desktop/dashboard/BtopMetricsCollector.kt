package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.MetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.Core
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuSnapshotData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.btopExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.isBraille
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.withCliPath
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.averageOrZero
import kotlin.collections.sortedBy

/**
 * Desktop (JVM/Unix) implementation of [MetricsCollector] that drives a `btop` instance inside a
 * `tmux` session and parses its panes for CPU/temperature/thread telemetry.
 *
 * Platform-specific: depends on `tmux`, `btop`, `ps`, and `pgrep` being available on the host.
 * The metrics dashboard lives in a tmux session ([tmuxSessionName]); [stopMetricsDashboard]
 * kills that session. Telemetry is sampled on demand via [parseBtopData] and the peak snapshot is
 * tracked internally.
 *
 * This collector holds mutable state (peak snapshot and the time-series list). It is intended to
 * be driven by a single owner — the [OllamaJobOrchestrator] sampling coroutine — and is **not**
 * safe for concurrent access from the UI thread and the collection coroutine; callers should not
 * invoke sampling and reading/resetting concurrently.
 */
class BtopMetricsCollector: MetricsCollector {

    /**
     * Maximum number of retained CPU time-series snapshots. When exceeded, the oldest snapshot is
     * dropped so the list cannot grow unbounded if the dashboard stays open for a long time.
     */
    private companion object {
        const val MAX_TIME_SERIES_SNAPSHOTS = 3_600
    }

    private val uiBorderRegex = Regex("[│┤┐└┴┬├─┼┘┌]")
    private val coreRegex = Regex("""C(\d+)\s+(\d+(?:\.\d+)?)%\s+(\d+)°C""")
    private val globalCpuTempRegex = Regex("""CPU\s+.*?\s+(\d+)°C""")
    private val btopCpuRegex = Regex("""CPU\s+■+\s+(\d+(?:\.\d+)?)%""")

    private var peakTemperature = 0
    private var peakProcessCpuConsumption = 0L
    private var peakCpuTelemetry = "PID: 0 | CPU: 0.0% | RAM Used: 0.0G"
    private var peakCores = mutableListOf<Core>()
    private var peakCpuGraph = mutableListOf<String>()
    private var peakThreadCount = 0
    private var peakBtopProcessCpuConsumption = 0.0
    private var peakAggregateCpuConsumption = 0.0
    private val cpuTimeSeriesSnapshots = mutableListOf<CpuTimeSeriesSnapshot>()

    override fun captureMetricsInWindowPane(targetPane: String): String {
        return try {
            val process = ProcessBuilder(tmuxExecutable, "capture-pane", "-p", "-t", targetPane, "-S", "-80")
                .withCliPath()
                .start()

            process.inputStream.bufferedReader().use {
                val text = it.readText()
                parseBtopDataFromString(text)
                text
            }.trimEnd()
        } catch (e: Exception) {
            println("Error capturing pane $targetPane: ${e.message}")
            "Pane error $targetPane: ${e.message}"
        }
    }

    override fun startMetricsDashboard() {
        try {
            val initProcess = ProcessBuilder(
                tmuxExecutable,
                "new-session",
                "-d",
                "-s",
                tmuxSessionName,
                btopExecutable,
            ).withCliPath().start()

            val initExitCode = initProcess.waitFor()
            if (initExitCode != 0) {
                val errorText = initProcess.errorStream.bufferedReader().use { it.readText() }
                error("tmux new-session failed with code $initExitCode: $errorText")
            }

            Thread.sleep(300)

            ProcessBuilder(tmuxExecutable, "rename-window", "-t", "${tmuxSessionName}:0", "Metrics")
                .withCliPath()
                .start()
                .waitFor()

            val splitResult = ProcessBuilder(
                tmuxExecutable,
                "split-window",
                "-h",
                "-p", "65",
                "-t",
                "${tmuxSessionName}:0",
                "bash",
                "-lc",
                gpuMonitoringCommand(),
            )
                .withCliPath()
                .start()

            val splitExitCode = splitResult.waitFor()
            if (splitExitCode != 0) {
                val errorText = splitResult.errorStream.bufferedReader().use { it.readText() }
                error("tmux split-window failed with code $splitExitCode: $errorText")
            }

            ProcessBuilder(tmuxExecutable, "select-layout", "-t", "${tmuxSessionName}:0", "even-horizontal")
                .withCliPath()
                .start()
                .waitFor()
        } catch (e: Exception) {
            println("Failed to configure tmux dashboard layout: ${e.message}")
        }
    }

    override fun stopMetricsDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
    }

    override suspend fun parseBtopData(): Result<OSMetrics> {
        val rawText = captureMetricsInWindowPane()

        if (rawText.startsWith("Pane error")) {
            println("CRITICAL: Tmux pane capture failed at the OS level!")
        }

        return parseBtopDataFromString(rawText)
    }

    private fun getCoreTemperatures(cpuSnapshotData: CpuSnapshotData): Int {
        return if (cpuSnapshotData.globalTemperature == 0 && cpuSnapshotData.cores.isNotEmpty()) {
            cpuSnapshotData.cores.map { it.temperature }.averageOrZero().toInt()
        } else {
            cpuSnapshotData.globalTemperature
        }
    }

    internal fun parseBtopDataFromString(rawText: String): Result<OSMetrics> {
        try {
            val cpuGraph = extractCpuGraph(rawText)
            val cleanText = rawText.sanitizeUIBorders()
            val lines = cleanText.lines()

            val cpuStats = parseGlobalCPUTempAndLoad(lines)
            val btopCpu = parseBtopCpuPercentage(rawText)
            val telemetry = scrapeBtopTelemetry(cpuStats, lines)
            val coreTemps = getCoreTemperatures(cpuStats)
            val threadCount = captureOllamaThreadCount()
            val aggregateCpu = cpuStats.cores.sumOf { it.cpuPercentage }

            getPeakMetrics(cpuStats, coreTemps, threadCount, telemetry, cpuGraph, btopCpu)

            cpuTimeSeriesSnapshots.add(
                CpuTimeSeriesSnapshot(
                    timestampMillis = System.currentTimeMillis(),
                    cpuConsumption = btopCpu,
                    aggregateCpuConsumption = aggregateCpu,
                    temperature = coreTemps,
                    threadCount = threadCount,
                )
            )
            if (cpuTimeSeriesSnapshots.size > MAX_TIME_SERIES_SNAPSHOTS) {
                cpuTimeSeriesSnapshots.removeAt(0)
            }

            val metrics = OSMetrics(
                cores = cpuStats.cores.sortedBy { it.name.substringAfter(" ").toIntOrNull() ?: 0 },
                temperature = coreTemps,
                processCpuConsumption = cpuStats.processCpu,
                cpuGraph = cpuGraph,
                cpuTelemetry = telemetry,
                threadCount = threadCount,
                btopProcessCpuConsumption = btopCpu,
                aggregateCpuConsumption = aggregateCpu,
            )

            return Result.Success(metrics)
        } catch (e: Exception) {
            println("Unable to get BtopMetrics. Cause of error: $e")
            return Result.Failure(e)
        }
    }

    override fun getPeakMetricsCollected(): OSMetrics {
        return OSMetrics(
            temperature = peakTemperature,
            processCpuConsumption = peakProcessCpuConsumption,
            cpuTelemetry = peakCpuTelemetry,
            cores = peakCores.sortedBy { it.name.substringAfter(" ").toIntOrNull() ?: 0 },
            cpuGraph = peakCpuGraph,
            threadCount = peakThreadCount,
            btopProcessCpuConsumption = peakBtopProcessCpuConsumption,
            aggregateCpuConsumption = peakAggregateCpuConsumption,
        )
    }

    override fun getCpuTimeSeriesSnapshots(): List<CpuTimeSeriesSnapshot> {
        return cpuTimeSeriesSnapshots.toList()
    }

    private fun getPeakMetrics(
        cpuStats: CpuSnapshotData,
        coreTemps: Int,
        threadCount: Int,
        telemetry: String,
        cpuGraph: List<String>,
        btopCpu: Double,
    ) {
        val aggregateCpu = cpuStats.cores.sumOf { it.cpuPercentage }
        val shouldUpdate = peakProcessCpuConsumption == 0L || peakTemperature == 0 ||
            cpuStats.processCpu >= peakProcessCpuConsumption || coreTemps >= peakTemperature ||
            threadCount >= peakThreadCount || btopCpu > peakBtopProcessCpuConsumption ||
            aggregateCpu > peakAggregateCpuConsumption
        if (shouldUpdate) {
            peakProcessCpuConsumption = maxOf(peakProcessCpuConsumption, cpuStats.processCpu)
            peakTemperature = maxOf(peakTemperature, coreTemps)
            peakThreadCount = maxOf(peakThreadCount, threadCount)
            peakBtopProcessCpuConsumption = maxOf(peakBtopProcessCpuConsumption, btopCpu)
            peakAggregateCpuConsumption = maxOf(peakAggregateCpuConsumption, aggregateCpu)
            peakCpuTelemetry = telemetry

            if (cpuStats.cores.isNotEmpty()) {
                peakCores = cpuStats.cores.toMutableList()
            }
            if (cpuGraph.isNotEmpty()) {
                peakCpuGraph = cpuGraph.toMutableList()
            }
        }
    }

    private fun parseBtopCpuPercentage(rawText: String): Double {
        return btopCpuRegex.find(rawText)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    }

    // find the core CPU rows by anchoring on the cpu header and the inner box layout
    override fun extractCpuGraph(rawBtopOutput: String): List<String> {
        val lines = rawBtopOutput.lines()
        val graphLines = mutableListOf<String>()

        for (line in lines) {
            if (line.contains("CPU ■") || line.contains("C0") || line.contains("C1")) {

                when {
                    // Row 1 - 22% ⣀⢠⣤⣤⣤  66°C
                    line.contains("CPU ■") -> {
                        val graphChunk = line.substringAfter("■ ").substringBefore(" ")
                        if (graphChunk.any { it.isBraille() }) graphLines.add(graphChunk)
                    }

                    // Row 2 -  ⢀⣼⣧⣀⣴│C0
                    line.contains("│C0") -> {
                        val graphChunk = line.substringBefore("│C0").trim().split(" ").last()
                        if (graphChunk.any { it.isBraille() }) graphLines.add(graphChunk)
                    }

                    // Row 3 - ⠈⢻⡟⠉⠻│C1
                    line.contains("│C1") -> {
                        val graphChunk = line.substringBefore("│C1").trim().split(" ").last()
                        if (graphChunk.any { it.isBraille() }) graphLines.add(graphChunk)
                    }
                }
            }
        }
        return graphLines
    }

    override fun resetPeakMetrics() {
        peakTemperature = 0
        peakProcessCpuConsumption = 0L
        peakCpuTelemetry = "PID: 0 | CPU: 0.0% | RAM Used: 0.0G"
        peakCores.clear()
        peakCpuGraph.clear()
        peakThreadCount = 0
        peakBtopProcessCpuConsumption = 0.0
        peakAggregateCpuConsumption = 0.0
    }

    override fun resetTimeSeriesSnapshots() {
        cpuTimeSeriesSnapshots.clear()
    }

    override fun resetCollectedMetrics() {
        resetPeakMetrics()
        resetTimeSeriesSnapshots()
    }

    private fun parseGlobalCPUTempAndLoad(lines: List<String>): CpuSnapshotData {
        var globalTemperature = 0
        val coresList = mutableListOf<Core>()

        println("DEBUG: Total lines sent to parser: ${lines.size}")

        try {
            lines.forEach { line ->
                if (line.contains("CPU")) {
                    globalCpuTempRegex.find(line)?.let { match ->
                        globalTemperature = match.groupValues[1].toIntOrNull() ?: 0
                    }
                }

                coreRegex.findAll(line).forEach { match ->
                    val coreNumber = match.groupValues[1]
                    val coreCpuPercent = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val coreTemp = match.groupValues[3].toIntOrNull() ?: 0
                    coresList.add(Core(name = "Core $coreNumber", temperature = coreTemp, cpuPercentage = coreCpuPercent))
                }
            }
        } catch(e: Exception) {
            println("Unable to parse global CPU temp and average loads. Cause: $e")
        }
        return CpuSnapshotData(
            globalTemperature = globalTemperature,
            processCpu = captureOllamaProcessCpuConsumption(),
            cores = coresList,
        )
    }

    private fun scrapeBtopTelemetry(cpuStats: CpuSnapshotData, lines: List<String>): String {
        return captureOllamaProcessTelemetry()
            ?: "PID: 0 | CPU: ${cpuStats.processCpu.toDouble()}% | RAM Used: 0.0G"
    }

    private fun captureOllamaThreadCount(): Int {
        return try {
            val pids = resolveOllamaRuntimePids()
            if (pids.isEmpty()) return 0

            pids.sumOf { pid ->
                val process = ProcessBuilder("ps", "-M", "-p", pid).start()
                val lines = process.inputStream.bufferedReader().use { it.readLines() }

                (lines.size - 1).coerceAtLeast(0)
            }
        } catch (e: Exception) {
            println("Failed to read system thread allocation: ${e.message}")
            0
        }
    }

    private fun resolveOllamaRuntimePids(): List<String> {
        val patterns = listOf("ollama serve", "llama-server")

        return patterns.flatMap { pattern ->
            try {
                val process = ProcessBuilder("pgrep", "-f", pattern).start()
                process.inputStream.bufferedReader()
                    .use { it.readLines() }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
        }.distinct()
    }

    private fun captureOllamaProcessCpuConsumption(): Long {
        return try {
            val process = ProcessBuilder("ps", "-axo", "pid,comm,pcpu,rss,command")
                .start()

            val lines = process.inputStream.bufferedReader().use { it.readLines() }

            lines
                .drop(1)
                .filter { line ->
                    line.contains("ollama serve", ignoreCase = true) ||
                            line.contains("llama-server", ignoreCase = true)
                }
                .sumOf { line ->
                    val columns = line.trim().split(Regex("\\s+"))
                    columns.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                }
                .toLong()
        } catch (e: Exception) {
            println("Failed to read Ollama process CPU usage: ${e.message}")
            0L
        }
    }

    private fun captureOllamaProcessTelemetry(): String? {
        return try {
            val process = ProcessBuilder("ps", "-axo", "pid,comm,pcpu,rss,command")
                .start()

            val lines = process.inputStream.bufferedReader().use { it.readLines() }

            val matchingRows = lines
                .drop(1)
                .filter { line ->
                    line.contains("ollama serve", ignoreCase = true) ||
                            line.contains("llama-server", ignoreCase = true)
                }

            if (matchingRows.isEmpty()) return null

            val totalCpu = matchingRows.sumOf { line ->
                val columns = line.trim().split(Regex("\\s+"))
                columns.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            }

            val totalRssKb = matchingRows.sumOf { line ->
                val columns = line.trim().split(Regex("\\s+"))
                columns.getOrNull(3)?.toLongOrNull() ?: 0L
            }

            val pids = matchingRows.mapNotNull { line ->
                line.trim().split(Regex("\\s+")).getOrNull(0)
            }

            val ramGb = totalRssKb / 1024.0 / 1024.0

            "PID(s): ${pids.joinToString(",")} | CPU: ${"%.1f".format(totalCpu)}% | RAM Used: ${"%.2f".format(ramGb)}G"
        } catch (e: Exception) {
            println("Failed to read Ollama process telemetry: ${e.message}")
            null
        }
    }

    private fun String.sanitizeUIBorders(): String = try {
        this.replace(uiBorderRegex, " ")
    } catch (e: Exception) {
        println("Border sanitizing failed. Cause: ${e.message}")
        ""
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }
}
