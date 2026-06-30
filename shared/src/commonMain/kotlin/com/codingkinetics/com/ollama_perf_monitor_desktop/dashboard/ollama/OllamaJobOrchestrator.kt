package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.RagasEngine
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.MetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.commandExists
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper.mapOllamaResponseToDomain
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.flatMap
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OllamaJobOrchestrator(
    val jobRunner: OllamaJobRunner,
    val metricsCollector: MetricsCollector,
    val ragasEngine: RagasEngine,
) {
    private var metricsSamplingJob: Job? = null

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
        getCurrentEssayText: () -> String,
    ): Result<PerformanceMetrics> {
        metricsCollector.resetPeakMetrics()
        startMetricsSampling()
        return when (val ollamaData = jobRunner.runOllamaEssayJob(model, prompt, onChunk)) {
            is Result.Success -> {
                stopMetricsSampling()
                logCompletedStats(ollamaData.data)
                val retrieveCurrentEssayText = getCurrentEssayText()
                evaluateRagasScore(prompt, retrieveCurrentEssayText, ollamaData.data)
            }
            is Result.Failure -> {
                stopMetricsSampling()
                ollamaData
            }
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
    ): Result<PerformanceMetrics> {
        val peakMetrics = metricsCollector.getPeakMetricsCollected()
        val finalMetrics = mapOllamaResponseToDomain(
            prompt = prompt,
            responsePayload = data,
            btopSnapshot = peakMetrics,
            ragasEvaluation
        )

        metricsCollector.stopStopDashboard()
        println("Final Metrics: $finalMetrics")
        return finalMetrics
    }

    private fun  logCompletedStats(data: OllamaResponseCompletedData) {
        println("------ Ollama Response Completed Data ----")
        println(data.toString())

        val rawStats = metricsCollector.captureMetricsInWindowPane()
        println("------BTOP Metrics Captured ----")
        println(rawStats)

    }

    internal fun startDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
        metricsCollector.startMetricsDashboard()
    }

    internal fun startMetricsSampling() {
        metricsSamplingJob?.cancel()
        metricsSamplingJob = CoroutineScope(Dispatchers.IO).launch {
            delay(100)
            while (isActive) {
                metricsCollector.parseBtopData()
                delay(100)
            }
        }
    }

    internal fun stopMetricsSampling() {
        metricsSamplingJob?.cancel()
        metricsSamplingJob = null
    }

    // TODO create a model object that captures graph state live as well
    internal fun captureTmuxPane(targetPane: String): String {
        val cpuGraph = metricsCollector.extractCpuGraph(targetPane)
        // TODO now do something with the graph like extract the data
        return metricsCollector.captureMetricsInWindowPane(targetPane)
    }

    internal fun cleanupRuntimeResources() {
        jobRunner.cleanupRuntimeResources()
    }
}