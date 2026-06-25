package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.btopExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.BtopMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.Core
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuSnapshotData
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.withCliPath
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

interface BtopMetricsCollector {
    fun captureTmuxPane(targetPane: String = "${tmuxSessionName}:0.0"): String
    fun startTmuxDashboard()
    fun stopTmuxDashboard()
    fun extractCpuGraph(rawBtopOutput: String): List<String>
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

            process.inputStream.bufferedReader().use {
                val text =  it.readText()
                print(text)
                text
            }.trimEnd()
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

    private fun getCoreTemperatures(cpuSnapshotData: CpuSnapshotData): Int {
        return if (cpuSnapshotData.globalTemperature == 0 && cpuSnapshotData.cores.isNotEmpty()) {
            cpuSnapshotData.cores.map { it.temperature }.roverage().toInt()
        } else {
            cpuSnapshotData.globalTemperature
        }
    }

    internal fun parseBtopDataFromString(rawText: String): Result<BtopMetrics> {
        try {
            val cpuGraph = extractCpuGraph(rawText)
            val cleanText = rawText.sanitizeUIBorders()
            println("DEBUG: Clean Text received from Btop: '$cleanText'")
            val lines = cleanText.lines()

            val cpuStats = parseGlobalCPUTempAndLoad(lines)
            val telemetry = scrapeBtopTelemetry(lines)
            val coreTemps = getCoreTemperatures(cpuStats)
            val threadCount = captureOllamaThreadCount()

            val metrics = BtopMetrics(
                cores = cpuStats.cores.sortedBy { it.name.substringAfter(" ").toIntOrNull() ?: 0 },
                temperature = coreTemps,
                processCpuConsumption = cpuStats.processCpu,
                cpuGraph = cpuGraph,
                cpuTelemetry = telemetry,
                threadCount = threadCount,
            )
            println("Parsed metrics: $metrics")
            return Result.Success(metrics)
        } catch (e: Exception) {
            println("Unable to get BtopMetrics. Cause of error: $e")
            return Result.Failure(e)
        }
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

    /**
     * Extension function to verify if a character belongs to the Unicode Braille Patterns block.
     * Unicode range: U+2800 – U+28FF
     */
    fun Char.isBraille(): Boolean {
        return this.code in 0x2800..0x28FF
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

    private fun scrapeBtopTelemetry(lines: List<String>): String {
        val cpuPercentage = lines.firstOrNull { it.matches(Regex("\\d+\\.\\d+")) }?.toDouble() ?: 0.0
        val pid = lines.firstOrNull { it.matches(Regex("\\d+")) && it.length >= 3 }?.toLong() ?: 0L

        val ramAllocation = lines.firstOrNull { it.contains(Regex("\\d+\\.\\d+[G|M]")) } ?: "0.0G"
        return "PID: $pid | CPU: $cpuPercentage% | RAM Used: $ramAllocation"
    }

    private fun captureOllamaThreadCount(): Int {
        return try {
            val pidProcess = ProcessBuilder("pgrep", "-f", "ollama serve").start()
            val pid = pidProcess.inputStream.bufferedReader().use { it.readText().trim() }

            if (pid.isBlank()) return 0

            val countPidThreads = ProcessBuilder("ps", "-M", "-p", pid).start()
            val lines = countPidThreads.inputStream.bufferedReader().use { it.readLines() }

            // subtract 1 to account for the command column header line
            (lines.size - 1).coerceAtLeast(0)
        } catch (e: Exception) {
            println("Failed to read system thread allocation: ${e.message}")
            0
        }
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
