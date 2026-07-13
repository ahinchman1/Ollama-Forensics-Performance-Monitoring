package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model

import androidx.compose.runtime.Immutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking.BenchmarkSuiteReport
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics

@Immutable
sealed interface DashboardViewState {

    data object Idle : DashboardViewState
    data class ActiveJob(
        val statusMessage: String = "Pipeline is starting...",
        val metricsPanel: String = "Initializing btop...",
        val currentScenario: String? = null,
        val essayText: String = "Preparing Ollama pre-fill..."
    ) : DashboardViewState

    sealed interface PipelineFailure : DashboardViewState {
        val errorMessage: String
        val installHint: String

        data class MissingDependency(
            override val errorMessage: String,
            override val installHint: String,
        ) : PipelineFailure

        data class ExecutionError(
            override val errorMessage: String,
            override val installHint: String = ""
        ) : PipelineFailure

        data class RateLimited(
            override val errorMessage: String,
            override val installHint: String = "Wait for the rate limit to reset before retrying.",
            val retryAfterSeconds: String = "60",
        ) : PipelineFailure
    }

    data class CompletedJob(
        val statusMessage: String,
        val metricsPanel: String,
        val currentScenario: String? = null,
        val essayText: String,
        val completedData: PerformanceMetrics,
    ): DashboardViewState

    data class BenchmarkResults(
        val report: BenchmarkSuiteReport,
    ): DashboardViewState
}