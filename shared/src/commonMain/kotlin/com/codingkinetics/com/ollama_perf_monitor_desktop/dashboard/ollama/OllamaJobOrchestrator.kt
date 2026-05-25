package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetricsCollectorImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxSessionName

class OllamaJobOrchestrator(
    val jobRunner: OllamaJobRunner = OllamaJobRunnerImpl(),
    val btopMetrics: BtopMetricsCollector = BtopMetricsCollectorImpl(),
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
        onAiJobCompleteUpdateGPUPanel: (OllamaResponseCompletedData) -> Unit,
    ): Result<Unit> =
        jobRunner.runOllamaEssayJob(model, prompt, onChunk) { data: OllamaResponseCompletedData ->
            onAiJobCompleteUpdateGPUPanel(data)
            println("------ Ollama Response Completed Data ----")
            println(data.toString())
            val rawStats = btopMetrics.captureTmuxPane()
            println("------BTOP Metrics Captured ----")
            println(rawStats.toString())
            // logAnalyzer.analyzeSnapshot(rawStats)

            // TODO finish mapping out OllamaResponse and find a way to render results
            val results=


            btopMetrics.stopTmuxDashboard()
        }

    internal fun startDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
        btopMetrics.startTmuxDashboard()
    }

    internal fun captureTmuxPane(targetPane: String): String {
        return btopMetrics.captureTmuxPane(targetPane)
    }

    internal fun cleanupRuntimeResources() {
        jobRunner.cleanupRuntimeResources()
    }
}