package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.MetricsAnalysis
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetricsCollectorImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper.mapOllamaResponseToDomain
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxSessionName

class OllamaJobOrchestrator(
    val jobRunner: OllamaJobRunner = OllamaJobRunnerImpl(),
    val btopMetrics: BtopMetricsCollector = BtopMetricsCollectorImpl(),
    val aiJobAnalyzer: MetricsAnalysis = MetricsAnalysis(),
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

    internal suspend fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
    ): Result<PerformanceMetrics> = when (val ollamaData = jobRunner.runOllamaEssayJob(model, prompt, onChunk)) {
        is Result.Success -> {
            logCompletedStats(ollamaData.data)
            getPerformanceData(prompt, ollamaData.data.response, ollamaData.data)
        }
        is Result.Failure -> {
            ollamaData
        }
    }

    private fun getPerformanceData(prompt: String, answer: String, data: OllamaResponseCompletedData): Result<PerformanceMetrics> =
        when (val parsedBtopMetrics = btopMetrics.parseBtopData()) {
            is Result.Success -> {
                val finalMetrics = mapOllamaResponseToDomain(
                    prompt = prompt,
                    responsePayload = data,
                    btopSnapshot = parsedBtopMetrics.data,
                )

                btopMetrics.stopTmuxDashboard()
                println("Final Metrics: $finalMetrics")
                finalMetrics
            }
            is Result.Failure -> {
                parsedBtopMetrics
            }
        }

    private fun  logCompletedStats(data: OllamaResponseCompletedData) {
        println("------ Ollama Response Completed Data ----")
        println(data.toString())

        val rawStats = btopMetrics.captureTmuxPane()
        println("------BTOP Metrics Captured ----")
        println(rawStats)

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