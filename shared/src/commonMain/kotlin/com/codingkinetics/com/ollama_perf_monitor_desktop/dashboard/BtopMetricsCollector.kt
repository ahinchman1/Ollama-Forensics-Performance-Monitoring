package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.BtopMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.Core
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuSnapshotData
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

interface BtopMetricsCollector {
    fun captureTmuxPane(targetPane: String = "$tmuxSessionName:0.0"): String
    fun startTmuxDashboard()
    fun stopTmuxDashboard()
    fun parseBtopData(): Result<BtopMetrics>
}

class BtopMetricsCollectorImpl: BtopMetricsCollector {

    private val uiBorderRegex = Regex("[│┤┐└┴┬├─┼┘┌]")
    private val coreRegex = Regex("""C(\d+)\s+\d+%\s+(\d+)°C""")
    private val globalCpuTempRegex = Regex("""CPU\s+.*?\s+(\d+)°C""")
    private val ollamaProcessRegex = Regex("""\d+\s+ollama\s+\S+\s+\S+\s+.*?(\d+\.\d+)\s*""")
    private val threadRegex = Regex("""(\d+)/(\d+)""")

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

    override fun stopTmuxDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
    }

    override fun parseBtopData(): Result<BtopMetrics> {
        val rawText = captureTmuxPane()

        println("DEBUG: Raw Text received from Tmux: '$rawText'")

        if (rawText.startsWith("Pane error")) {
            println("CRITICAL: Tmux pane capture failed at the OS level!")
        }

        return parseBtopDataFromString(rawText)
    }

    internal fun parseBtopDataFromString(rawText: String): Result<BtopMetrics> {
        try {
            val cleanText = rawText.sanitizeUIBorders()
            val lines = cleanText.lines()

            val cpuStats = parseGlobalCPUTempAndLoad(lines)
            val totalThreads = extractSystemStatesFromFallbackScanners(lines)

            val metrics =  BtopMetrics(
                cores = cpuStats.cores.sortedBy { it.name.substringAfter(" ").toIntOrNull() ?: 0 },
                temperature = if (cpuStats.globalTemperature == 0 && cpuStats.cores.isNotEmpty()) {
                    cpuStats.cores.map { it.temperature }.roverage().toInt()
                } else {
                    cpuStats.globalTemperature
                },
                processCpuConsumption = cpuStats.processCpu,
                cpuTelemetry = cleanText,
                threadCount = totalThreads,
            )
            println("Parsed metrics: $metrics")
            return Result.Success(metrics)
        } catch (e: Exception) {
            println("Unable to get BtopMetrics. Cause of error: $e")
            return Result.Failure(e)
        }
    }

    private fun parseGlobalCPUTempAndLoad(lines: List<String>): CpuSnapshotData {
        var globalTemperature = 0
        var processCpu = 0L
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
                    val coreTemp = match.groupValues[2].toIntOrNull() ?: 0
                    coresList.add(Core(name = "Core $coreNumber", temperature = coreTemp))
                }

                if (line.contains("ollama")) {
                    ollamaProcessRegex.find(line)?.let { match ->
                        processCpu = match.groupValues[1].toDoubleOrNull()?.toLong() ?: 0L
                    }
                }
            }
        } catch(e: Exception) {
            println("Unable to parse global CPU temp and average loads. Cause: $e")
        }
        return CpuSnapshotData(globalTemperature, processCpu, coresList)
    }

    private fun extractSystemStatesFromFallbackScanners(lines: List<String>): Int {
        val statusLine = lines.find { line ->
            line.contains("/") && (line.contains("signals") || line.contains("info"))
        } ?: return 0

        val match = threadRegex.find(statusLine)

        return match?.groupValues?.get(2)?.toIntOrNull() ?: 0
    }

    private fun String.sanitizeUIBorders(): String = try {
        this.replace(uiBorderRegex, " ")
    } catch (e: Exception) {
        println("Border sanitizing failed. Cause: ${e.message}")
        ""
    }

    private fun List<Int>.roverage(): Double = try {
        if (isEmpty()) 0.0 else this.sum().toDouble() / this.size
    } catch(e: Exception) {
        println("roverage() failed: Cause: $e")
        0.0
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }
}
