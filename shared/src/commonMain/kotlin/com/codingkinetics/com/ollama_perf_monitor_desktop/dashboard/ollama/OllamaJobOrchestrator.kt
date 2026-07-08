package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper.mapOllamaResponseToDomain
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.RagasEngine
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.MetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaJobResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.ScenarioTimeSeries
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.TokenTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.flatMap
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.commandExists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class OllamaJobOrchestrator(
    private val jobRunner: OllamaJobRunner,
    private val metricsCollector: MetricsCollector,
    private val ragasEngine: RagasEngine,
    private val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProviderImpl(),
    private val scope: CoroutineScope? = null,
) {
    private var metricsSamplingJob: Job? = null

    private val ownsScope = scope == null
    private val internalScope = if (ownsScope) CoroutineScope(coroutineContextProvider.ioDispatcher) else null

    private val samplingScope: CoroutineScope
        get() = scope ?: internalScope!!

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
        onTokenProgress: (promptEvalCount: Long, evalCount: Long) -> Unit = { _, _ -> },
    ): Result<PerformanceMetrics> {
        startMetricsSampling()
        return when (val ollamaData = jobRunner.runOllamaEssayJob(model, prompt, onChunk, onTokenProgress)) {
            is Result.Success -> {
                val peakMetrics = metricsCollector.getPeakMetricsCollected()
                stopMetricsSampling()
                logCompletedStats(ollamaData.data)
                metricsCollector.resetPeakMetrics()
                evaluateRagasScore(prompt, ollamaData.data, peakMetrics)
            }
            is Result.Failure -> {
                stopMetricsSampling()
                ollamaData
            }
        }
    }

    private suspend fun evaluateRagasScore(
        prompt: String,
        jobResult: OllamaJobResult,
        peakMetrics: OSMetrics,
    ): Result<PerformanceMetrics> =
        ragasEngine.calculateHallucinationScore(
            prompt,
            jobResult.generatedText,
            peakMetrics,
        ).flatMap { evalData ->
            getPerformanceData(prompt, jobResult, evalData, peakMetrics)
        }

    private fun getPerformanceData(
        prompt: String,
        jobResult: OllamaJobResult,
        ragasEvaluation: EvaluationResult,
        peakMetrics: OSMetrics,
    ): Result<PerformanceMetrics> {
        val finalMetrics = mapOllamaResponseToDomain(
            prompt = prompt,
            ollamaJobResult = jobResult,
            btopSnapshot = peakMetrics,
            ragasEvaluation
        )

        println("Final Metrics: $finalMetrics")
        return finalMetrics
    }

    private fun logCompletedStats(data: OllamaJobResult) {
        println("------ Ollama Response Completed Data ----")
        println(data.completedData.toString())

        val rawStats = metricsCollector.captureMetricsInWindowPane("${tmuxSessionName}:0.0")
        println("------BTOP Metrics Captured ----")
        println(rawStats)

    }

    internal fun startDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
        metricsCollector.startMetricsDashboard()
    }

    internal fun startMetricsSampling() {
        metricsSamplingJob?.cancel()
        metricsSamplingJob = samplingScope.launch {
            while (isActive) {
                metricsCollector.parseBtopData()
                kotlinx.coroutines.delay(1_000.milliseconds)
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
        stopMetricsSampling()
        internalScope?.cancel()
        metricsCollector.stopMetricsDashboard()
        jobRunner.cleanupRuntimeResources()
    }

    internal fun getCpuTimeSeriesSnapshots(): List<CpuTimeSeriesSnapshot> {
        return metricsCollector.getCpuTimeSeriesSnapshots()
    }

    internal fun resetTimeSeriesSnapshots() {
        metricsCollector.resetTimeSeriesSnapshots()
    }
}