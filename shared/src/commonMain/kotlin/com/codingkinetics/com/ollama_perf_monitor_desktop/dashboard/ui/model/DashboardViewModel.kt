package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.btopExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollamaExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.tmuxSessionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
) {
    private val _viewState = MutableStateFlow<DashboardViewState>(DashboardViewState.Idle)
    val viewState: StateFlow<DashboardViewState> = _viewState.asStateFlow()
    private var observabilityJob: Job? = null
    private val ollamaModel = "llama3.2"

    fun startPipeline(onEssayChunkReceived: (String) -> Unit) {
        _viewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Starting dashboard...",
            metricsPanel = "Starting metrics panel...",
            gpuPanel = "Starting GPU / system panel...",
            essayText = "Preparing Ollama essay job..."
        )

        scope.launch(contextPool.mainImmediateDispatcher) {
            try {
                withContext(contextPool.ioDispatcher) {
                    when (val checkDep = ollamaJobOrchestrator.checkMonitoringToolDependency()) {
                        is Result.Success -> {
                            ollamaJobOrchestrator.startDashboard()
                            ollamaJobOrchestrator.startServer()
                        }

                        is Result.Failure -> _viewState.update {
                            DashboardViewState.Error(
                                checkDep.exception.message ?: "Unknown error",
                                tmuxExecutable,
                                btopExecutable,
                                ollamaExecutable,
                            )
                        }
                    }
                }

                _viewState.update { currentState ->
                    if (currentState is DashboardViewState.ActiveJob) {
                        currentState.copy(
                            statusMessage = "Dashboard running. Ollama essay generation is starting.",
                            essayText = "Waiting for Ollama pre-fill..."
                        )
                    } else currentState
                }

                synchronousRefresh()
                startObservabilityStream()

                withContext(contextPool.ioDispatcher) {
                    ollamaJobOrchestrator.runOllamaEssayJob(
                        model = ollamaModel,
                        prompt = prompt,
                        onChunk = updateText { onEssayChunkReceived(it) },
                        onAiJobComplete = { data -> onAiJobComplete(data) },
                    )
                }

                updatePipelineStatus(isComplete = true)
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupRuntimeResources()

                updatePipelineStatus(isComplete = false, failureMessage = e.message ?: e::class.simpleName)
                _viewState.value = DashboardViewState.Error(
                    errorMessage = "Pipeline Error: ${e.message ?: e::class.simpleName}",
                    tmuxPath = tmuxExecutable,
                    btopPath = btopExecutable,
                    ollamaPath = ollamaExecutable
                )
            }
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
        scope.launch {
            cleanupRuntimeResources()
            _viewState.value = DashboardViewState.Idle
        }
    }

    fun clearRuntimeResources() {
        stopPipeline()
    }

    suspend fun synchronousRefresh() {
        val metrics = withContext(contextPool.ioDispatcher) {
            ollamaJobOrchestrator.captureMetricsData("${tmuxSessionName}:0.0")
        }

        val gpu = withContext(contextPool.ioDispatcher) {
            ollamaJobOrchestrator.captureMetricsData("${tmuxSessionName}:0.1")
        }

        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                currentState.copy(metricsPanel = metrics, gpuPanel = gpu)
            } else currentState
        }
    }

    fun startObservabilityStream() {
        observabilityJob?.cancel()

        observabilityJob = scope.launch(contextPool.mainImmediateDispatcher) {
            _viewState.update { currentState ->
                if (currentState is DashboardViewState.ActiveJob) {
                    currentState.copy(
                        statusMessage = "Running Ollama model '$ollamaModel'...",
                        essayText = "Waiting for Ollama pre-fill..."
                    )
                } else currentState
            }
        }
    }

    fun updatePipelineStatus(isComplete: Boolean, failureMessage: String? = null) {
        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                if (isComplete) {
                    currentState.copy(statusMessage = "Ollama essay generation complete. Monitoring panels active.")
                } else {
                    currentState.copy(
                        statusMessage = "Ollama essay generation failed.",
                        essayText = failureMessage ?: "Ollama pipeline failure encountered."
                    )
                }
            } else currentState
        }
    }

    private suspend fun cleanupRuntimeResources() {
        observabilityJob?.cancel()
        observabilityJob = null

        withContext(contextPool.ioDispatcher) {
            ollamaJobOrchestrator.cleanupRuntimeResources()
        }
    }

    private fun onAiJobComplete(finalData: OllamaResponseCompletedData) =
        scope.launch(contextPool.mainImmediateDispatcher) {
            _viewState.update { currentState ->
                (if (currentState is DashboardViewState.ActiveJob) {
                    println("Ollama essay generation complete. Monitoring panels active.")
                    DashboardViewState.CompletedJob(
                        currentState.statusMessage,
                        currentState.metricsPanel,
                        currentState.gpuPanel,
                        currentState.essayText,
                        finalData,
                    )
                } else currentState)
            }
        }

    companion object {
        val prompt = """
            Write a clear technical essay about JVM concurrency and local AI inference.
            Focus on:
             - why local inference is useful for experimentation
             - how JVM concurrency affects responsiveness
             - why separating monitoring panels from generated prose improves the UI
             - how observability helps compare CPU, memory, GPU, and process behavior
    
            Keep the tone practical, technical, and research-oriented.""".trimIndent()
    }
}