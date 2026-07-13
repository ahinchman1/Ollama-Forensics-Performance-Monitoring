package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.formatter.PerformanceMetricsFormatter
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.ForensicsEvaluator
import com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking.ForensicsBenchmarkSuite
import com.codingkinetics.com.ollama_perf_monitor_desktop.di.OLLAMA_MODEL
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.openFile
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DashboardViewModel(
    private val scope: CoroutineScope,
    private val ollamaJobOrchestrator: OllamaJobOrchestrator,
    private val contextPool: CoroutineContextProvider = CoroutineContextProviderImpl(),
    private val metricsFormatter: PerformanceMetricsFormatter = PerformanceMetricsFormatter(),
    private val ollamaModel: String = OLLAMA_MODEL,
) {
    private val _viewState = MutableStateFlow<DashboardViewState>(DashboardViewState.Idle)
    val viewState: StateFlow<DashboardViewState> = _viewState.asStateFlow()

    companion object {
        const val MaxBenchmarkLogChars = 20_000

        val prompt = """
            Project Architecture Constraints:
                We are building a highly concurrent Kotlin transaction engine. It needs to be fast, handle 
                signature verification safely in the background, and drop things if it gets too overloaded 
                so it doesn't crash the JVM
                    
            Prompt
                Write the complete production implementation. Make sure it explicitly uses a single-threaded 
                event loop for the state changes and applies strict backpressure.""".trimIndent()
    }

    private fun appendBoundedLog(existing: String, addition: String): String {
        val combined = existing + addition
        return if (combined.length <= MaxBenchmarkLogChars) {
            combined
        } else {
            combined.takeLast(MaxBenchmarkLogChars)
        }
    }

    fun startPipeline(onEssayChunkReceived: (String) -> Unit) {
        _viewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Starting dashboard...",
            metricsPanel = "Starting metrics panel...",
            currentScenario = "Live Pipeline",
            essayText = "Preparing Ollama essay job..."
        )

        scope.launch(contextPool.ioDispatcher) {
            when (val checkDep = ollamaJobOrchestrator.checkMonitoringToolDependency()) {
                is Result.Success -> {
                    ollamaJobOrchestrator.startDashboard()
                    ollamaJobOrchestrator.startServer()
                    withContext(contextPool.mainImmediateDispatcher) {
                        updateToActiveJob()
                    }
                    refreshMonitoringPanels()

                    getPerformanceData(onEssayChunkReceived)

                }
                is Result.Failure -> withContext(contextPool.mainDispatcher) {
                    val failureMessage = checkDep.exception.message ?: checkDep.exception::class.simpleName
                    println(failureMessage)
                    val installHint = when (checkDep.exception) {
                        is IllegalStateException -> checkDep.exception.message ?: ""
                        else -> "Unknown error"
                    }
                    _viewState.update {
                        DashboardViewState.PipelineFailure.MissingDependency(
                            errorMessage = failureMessage ?: "Unknown error",
                            installHint = installHint,
                        )
                    }
                }
            }
        }
    }

    private suspend fun getPerformanceData(onEssayChunkReceived: (String) -> Unit) {
        when (val performanceMetrics = ollamaJobOrchestrator.runOllamaEssayJob(
            model = ollamaModel,
            prompt = prompt,
            onChunk = updateText { onEssayChunkReceived(it) },
        )) {
            is Result.Success -> withContext(contextPool.mainDispatcher) {
                println("Job completed.")
                onAiJobComplete(performanceMetrics.data)
                cleanupRuntimeResources()
            }

            is Result.Failure -> withContext(contextPool.mainDispatcher) {
                val e = performanceMetrics.exception
                println("Unable to get performance data. Cause: ${e.message}")
                val rateLimitEx = e as? ForensicsEvaluator.GroqRateLimitException
                _viewState.update {
                    if (rateLimitEx != null) {
                        DashboardViewState.PipelineFailure.RateLimited(
                            errorMessage = rateLimitEx.message ?: "Rate limited by Groq API",
                            retryAfterSeconds = rateLimitEx.retryAfterSeconds,
                        )
                    } else {
                        DashboardViewState.PipelineFailure.ExecutionError(
                            errorMessage = e.message ?: "Unknown error",
                        )
                    }
                }
            }
        }
    }

    private fun updateToActiveJob() {
        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                currentState.copy(
                    statusMessage = "Dashboard running. Ollama essay generation is starting.",
                    essayText = "Waiting for Ollama pre-fill...",
                )
            } else currentState
        }
    }

    private fun updateText(onEssayChunkReceived: (String) -> Unit): (String) -> Unit =
        { chunk ->
            _viewState.update { currentState ->
                if (currentState is DashboardViewState.ActiveJob) {
                    currentState.copy(
                        essayText = appendBoundedLog(currentState.essayText, chunk),
                    )
                } else currentState
            }

            onEssayChunkReceived(chunk)
        }

    fun stopPipeline() {
        scope.launch(contextPool.ioDispatcher) {
            cleanupRuntimeResources()
            withContext(contextPool.mainDispatcher) {
                _viewState.value = DashboardViewState.Idle
            }
        }
    }

    fun runBenchmark() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val logFile = File(System.getProperty("user.home"), ".ollama-perf-monitor/logs/benchmark_$timestamp.log").apply {
            parentFile?.mkdirs()
        }

        _viewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Starting benchmark suite...",
            metricsPanel = "Initializing benchmark...",
            currentScenario = null,
            essayText = "Preparing benchmark scenarios...",
        )

        scope.launch(contextPool.ioDispatcher) {
            try {
                val suite = ForensicsBenchmarkSuite(
                    orchestrator = ollamaJobOrchestrator,
                    model = ollamaModel,
                )
                val report = suite.runSuite(
                    logFile = logFile,
                    onProgress = { status ->
                        _viewState.update { current ->
                            if (current is DashboardViewState.ActiveJob) {
                                val separator = if (current.essayText.isBlank()) "" else "\n\n"
                                current.copy(
                                    essayText = appendBoundedLog(
                                        current.essayText,
                                        separator + status,
                                    ),
                                    currentScenario = status,
                                )
                            } else current
                        }
                    },
                    onChunk = { chunk ->
                        _viewState.update { current ->
                            if (current is DashboardViewState.ActiveJob) {
                                current.copy(
                                    essayText = appendBoundedLog(current.essayText, chunk)
                                )
                            } else current
                        }
                    },
                )
                withContext(contextPool.mainDispatcher) {
                    _viewState.value = DashboardViewState.BenchmarkResults(report)
                    openFile(logFile)
                }
            } catch (e: Exception) {
                withContext(contextPool.mainDispatcher) {
                    val rateLimitEx = e as? ForensicsEvaluator.GroqRateLimitException
                    _viewState.value = if (rateLimitEx != null) {
                        DashboardViewState.PipelineFailure.RateLimited(
                            errorMessage = rateLimitEx.message ?: "Rate limited by Groq API",
                            retryAfterSeconds = rateLimitEx.retryAfterSeconds,
                        )
                    } else {
                        DashboardViewState.PipelineFailure.ExecutionError(
                            errorMessage = "Benchmark failed: ${e.message}",
                        )
                    }
                }
            }
        }
    }

    fun clearRuntimeResources() {
        scope.launch(contextPool.ioDispatcher) {
            ollamaJobOrchestrator.cleanupRuntimeResources()
        }
    }

    suspend fun refreshMonitoringPanels() {
        val metrics = ollamaJobOrchestrator.captureTmuxPane("${tmuxSessionName}:0.0")

        withContext(contextPool.mainDispatcher) {
            _viewState.update { currentState ->
                if (currentState is DashboardViewState.ActiveJob) {
                    currentState.copy(metricsPanel = metrics)
                } else currentState
            }
        }
    }

    private fun cleanupRuntimeResources() {
        ollamaJobOrchestrator.cleanupRuntimeResources()
    }

    private fun onAiJobComplete(metrics: PerformanceMetrics) {
        val processedMetricsLayout = metricsFormatter.formatDiagnostics(metrics, ollamaModel)
        val systemsPanelSummary = metricsFormatter.formatSystemSnapshot(metrics)

        val logFile = File(System.getProperty("user.home"), ".ollama-perf-monitor/logs/job.log").apply {
            parentFile?.mkdirs()
        }
        val logContent = buildString {
            appendLine("===== Ollama Job Diagnostics =====")
            appendLine(processedMetricsLayout)
            appendLine()
            appendLine("===== System Snapshot =====")
            appendLine(systemsPanelSummary)
        }
        runCatching { logFile.writeText(logContent) }
            .onFailure { println("Unable to write job log. Cause: ${it.message}") }

        println(processedMetricsLayout)
        println(systemsPanelSummary)

        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                DashboardViewState.CompletedJob(
                    statusMessage = "Job finished successfully.",
                    metricsPanel = processedMetricsLayout,
                    currentScenario = "Completed",
                    completedData = metrics,
                    essayText = currentState.essayText,
                )
            } else currentState
        }

        openFile(logFile)
    }

}