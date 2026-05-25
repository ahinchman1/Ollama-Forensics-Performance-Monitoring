package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.BtopMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.Core
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuSnapshotData

interface BtopMetricsCollector {
    fun captureTmuxPane(targetPane: String = "$tmuxSessionName:0.0"): String
    fun startTmuxDashboard()
    fun stopTmuxDashboard()
    fun parseBtopData(): BtopMetrics
}

class BtopMetricsCollectorImpl: BtopMetricsCollector {

    private val uiBorderRegex = Regex("[│┤┐└┴┬├─┼┘┌]")

    // Regex to pluck core patterns like "C0 96% 81°C" or "C12 8% 75°C"
    private val coreRegex = Regex("""C(\d+)\s+\d+%\s+(\d+)°C""")

    // Regex to isolate the specific line tracking the ollama process metrics
    // i.e. target: "39408 ollama  aman+  2.4G ⣤⣤⣤⣤⣤ 49.4"
    private val ollamaProcessRegex = Regex("""\d+\s+ollama\s+\S+\s+\S+\s+[^0-9]*(\d+\.\d+)""")

    override fun captureTmuxPane(targetPane: String): String {
        return try {
            val process = ProcessBuilder(tmuxExecutable, "capture-pane", "-p", "-t", targetPane, "-S", "-80")
                .withCliPath()
                .start()

            process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
        } catch (e: Exception) {
            println("Error capturing pane $targetPane: ${e.message}")
            "Pane error $targetPane: ${e.message}"
        }
    }

    override fun startTmuxDashboard() {
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
                "-h",       // horizontal-split
                "-p", "65", // takes 65 percent of the window
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
            throw IllegalStateException("Failed to configure tmux dashboard layout: ${e.message}", e)
        }
    }

    override fun stopTmuxDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
    }

    override fun parseBtopData(): BtopMetrics {
        val rawText = captureTmuxPane()
        val cleanText = rawText.sanitizeUIBorders()
        val lines = cleanText.lines()

        // Extract hardware states cleanly via a dedicated calculation holder
        val cpuStats = parseGlobalCPUTempAndLoad(lines)
        val totalThreads = extractSystemStatesFromFallbackScanners(lines)

        return BtopMetrics(
            cores = cpuStats.cores.sortedBy { it.name.substringAfter(" ").toIntOrNull() ?: 0 },
            temperature = if (cpuStats.globalTemperature == 0 && cpuStats.cores.isNotEmpty()) {
                cpuStats.cores.map { it.temperature }.roverage().toInt()
            } else {
                cpuStats.globalTemperature
            },
            processCpuConsumption = cpuStats.processCpu,
            cpuTelemetry = cleanText,
            threadCount = totalThreads
        )
    }

    private fun parseGlobalCPUTempAndLoad(lines: List<String>): CpuSnapshotData {
        var globalTemperature = 0
        var processCpu = 0L
        val coresList = mutableListOf<Core>()

        lines.forEach { line ->
            if (line.contains("CPU") && line.contains("°C")) {
                globalTemperature = line.substringAfter("°C")
                    .substringBeforeLast("°C")
                    .trim()
                    .split(" ")
                    .lastOrNull()
                    ?.replace(Regex("[^0-9]"), "")
                    ?.toIntOrNull() ?: 0
            }

            coreRegex.findAll(line).forEach { match ->
                val coreNumber = match.groupValues[1]
                val coreTemp = match.groupValues[2].toIntOrNull() ?: 0
                coresList.add(Core(name = "Core $coreNumber", temperature = coreTemp))
            }

            if (line.contains("ollama")) {
                ollamaProcessRegex.find(line)?.let { match ->
                    processCpu = match.groupValues[1].toDoubleOrNull()?.toLong() ?: 0L
                }
            }
        }

        return CpuSnapshotData(globalTemperature, processCpu, coresList)
    }

    private fun extractSystemStatesFromFallbackScanners(lines: List<String>): String {
        return lines.find { line -> line.contains("signals") || line.contains("/") }
            ?.substringAfterLast(" ")
            ?.substringBefore("/")
            ?.trim() ?: "0"
    }

    private fun String.sanitizeUIBorders(): String = this.replace(uiBorderRegex, " ")

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }
}