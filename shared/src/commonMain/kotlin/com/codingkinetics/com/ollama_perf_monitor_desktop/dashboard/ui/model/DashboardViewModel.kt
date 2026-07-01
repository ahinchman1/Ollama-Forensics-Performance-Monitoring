package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.formatter.PerformanceMetricsFormatter
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(
    private val scope: CoroutineScope,
    private val ollamaJobOrchestrator: OllamaJobOrchestrator,
    private val contextPool: CoroutineContextProvider = CoroutineContextProviderImpl(),
    private val metricsFormatter: PerformanceMetricsFormatter = PerformanceMetricsFormatter(),
) {
    private val _viewState = MutableStateFlow<DashboardViewState>(DashboardViewState.Idle)
    val viewState: StateFlow<DashboardViewState> = _viewState.asStateFlow()
    private val ollamaModel = "llama3.2"

    fun startPipeline(onEssayChunkReceived: (String) -> Unit) {
        _viewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Starting dashboard...",
            metricsPanel = "Starting metrics panel...",
            gpuPanel = "Starting GPU / system panel...",
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
                _viewState.update {
                    DashboardViewState.PipelineFailure.ExecutionError(
                        errorMessage = performanceMetrics.exception.message ?: "Unknown error",
                    )
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
                    val cleanText = if (currentState.essayText == "Waiting for Ollama pre-fill...") {
                        ""
                    } else {
                        currentState.essayText
                    }
                    currentState.copy(essayText = cleanText + chunk)
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

    fun clearRuntimeResources() {
        scope.launch(contextPool.ioDispatcher) {
            ollamaJobOrchestrator.cleanupRuntimeResources()
        }
    }

    suspend fun refreshMonitoringPanels() {
        val metrics = ollamaJobOrchestrator.captureTmuxPane("${tmuxSessionName}:0.0")
        val gpu = ollamaJobOrchestrator.captureTmuxPane("${tmuxSessionName}:0.1")


        withContext(contextPool.mainDispatcher) {
            _viewState.update { currentState ->
                if (currentState is DashboardViewState.ActiveJob) {
                    currentState.copy(metricsPanel = metrics, gpuPanel = gpu)
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

        println(processedMetricsLayout)
        println(systemsPanelSummary)

        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                DashboardViewState.CompletedJob(
                    statusMessage = "Job finished successfully.",
                    metricsPanel = processedMetricsLayout,
                    gpuPanel = systemsPanelSummary,
                    completedData = metrics,
                    essayText = currentState.essayText,
                )
            } else currentState
        }
    }

    companion object {
        val prompt = """
            Project Architecture Constraints:
                We are building a highly concurrent Kotlin transaction engine. It needs to be fast, handle 
                signature verification safely in the background, and drop things if it gets too overloaded 
                so it doesn't crash the JVM
                    
            Prompt
                Write the complete production implementation. Make sure it explicitly uses a single-threaded 
                event loop for the state changes and applies strict backpressure.""".trimIndent()
    }
}