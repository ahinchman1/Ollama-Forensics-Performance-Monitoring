package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.btopExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.resolveExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.withCliPath
import java.io.File
import java.util.concurrent.TimeUnit

class OllamaJobOrchestrator(
    val jobRunner: OllamaJobRunner = OllamaJobRunnerImpl(),
    val btopMetrics: BtopMetrics = BtopMetrics(),
) {

    internal fun checkMonitoringToolDependency(): Result<Unit> {
        return when {
            !commandExists("tmux") ->
                Result.Failure(IllegalStateException("tmux is required but was not found."))
            !commandExists("btop") ->
                Result.Failure(IllegalStateException("btop is required but was not found. Install it with Homebrew or your package manager."))
            !commandExists("ollama") ->
                Result.Failure(IllegalStateException("ollama is required but was not found. Install it with Homebrew or your package manager."))
            else -> Result.Success(Unit)
        }
    }

    fun startServer() = jobRunner.startOllamaServer()

    internal fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onAiJobComplete: (OllamaResponseCompletedData) -> Unit = {},
    ): Result<Unit> = jobRunner.runOllamaEssayJob(model, prompt, onChunk, onAiJobComplete)

    internal fun startDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)

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

    internal fun captureMetricsData(targetPane: String): String {
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

    internal fun commandExists(command: String): Boolean {
        val executable = resolveExecutable(command)
        if (File(executable).isAbsolute && File(executable).exists()) {
            return true
        }

        return try {
            val process = ProcessBuilder("bash", "-c", "command -v $command")
                .withCliPath()
                .start()
            process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }

    internal fun cleanupRuntimeResources() {
        jobRunner.cleanupRuntimeResources()
    }
}