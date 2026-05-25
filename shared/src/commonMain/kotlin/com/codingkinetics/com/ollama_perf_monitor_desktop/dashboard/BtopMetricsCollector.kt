package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

interface BtopMetricsCollector {
    fun captureTmuxPane(targetPane: String = "$tmuxSessionName:0.0"): String
    fun startTmuxDashboard()
    fun stopTmuxDashboard()
    fun parseBtopData(): Map<String, String>
}

class BtopMetricsCollectorImpl: BtopMetricsCollector {

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

    override fun parseBtopData(): Map<String, String> {
        val rawText = captureTmuxPane()
        val metrics = mutableMapOf<String, String>()

        // Clean up character noise from btop boxes so we only evaluate standard text/numbers
        val cleanText = rawText.replace(Regex("[│┤┐└┴┬├─┼┘┌]"), " ")

        // Simple line-by-line scanning for keyword positions
        cleanText.lines().forEach { line ->
            when {
                line.contains("Cpu:", ignoreCase = true) -> {
                    metrics["cpu_usage"] = line.substringAfter("Cpu:").trim().split(" ").firstOrNull() ?: ""
                }
                line.contains("Mem:", ignoreCase = true) -> {
                    metrics["mem_usage"] = line.substringAfter("Mem:").trim().split(" ").firstOrNull() ?: ""
                }
            }
        }
        return metrics
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }
}