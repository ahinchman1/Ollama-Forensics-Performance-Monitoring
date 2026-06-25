package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.RagasEngine
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.BtopMetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper.mapOllamaResponseToDomain
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.flatMap
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName

class OllamaJobOrchestrator(
    val jobRunner: OllamaJobRunner,
    val btopMetrics: BtopMetricsCollector,
    val ragasEngine: RagasEngine,
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
            evaluateRagasScore(prompt, ollamaData.data.response, ollamaData.data)
        }
        is Result.Failure -> {
            ollamaData
        }
    }

    private suspend fun evaluateRagasScore(
        prompt: String,
        response: String,
        ollamaData: OllamaResponseCompletedData,
    ): Result<PerformanceMetrics> =
        ragasEngine.calculateHallucinationScore(prompt, response).flatMap { evalData ->
            getPerformanceData(prompt, ollamaData, evalData)
        }

    private fun getPerformanceData(
        prompt: String,
        data: OllamaResponseCompletedData,
        ragasEvaluation: EvaluationResult,
    ): Result<PerformanceMetrics> =
        when (val parsedBtopMetrics = btopMetrics.parseBtopData()) {
            is Result.Success -> {
                val finalMetrics = mapOllamaResponseToDomain(
                    prompt = prompt,
                    responsePayload = data,
                    btopSnapshot = parsedBtopMetrics.data,
                    ragasEvaluation
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

    // TODO create a model object that captures graph state live as well
    internal fun captureTmuxPane(targetPane: String): String {
        val cpuGraph = btopMetrics.extractCpuGraph(targetPane)
        // TODO now do something with the graph like extract the data
        return btopMetrics.captureTmuxPane(targetPane)
    }

    internal fun cleanupRuntimeResources() {
        jobRunner.cleanupRuntimeResources()
    }
}