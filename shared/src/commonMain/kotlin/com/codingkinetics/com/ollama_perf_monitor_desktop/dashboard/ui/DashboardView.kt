package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking.BenchmarkReportView
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewState
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DashboardView(viewModel: DashboardViewModel) {
    val uiState by viewModel.viewState.collectAsState()

    LaunchedEffect(uiState is DashboardViewState.ActiveJob) {
        while (isActive && uiState is DashboardViewState.ActiveJob) {
            viewModel.refreshMonitoringPanels()
            delay(1000)
        }
    }

    val currentStatusMessage = when (val state = uiState) {
        is DashboardViewState.Idle -> "System Ready. Infrastructure offline."
        is DashboardViewState.ActiveJob -> state.statusMessage
        is DashboardViewState.PipelineFailure -> when (state) {
            is DashboardViewState.PipelineFailure.RateLimited -> "RATE LIMITED: ${state.errorMessage}"
            else -> "FAILURE: ${state.errorMessage}"
        }
        is DashboardViewState.CompletedJob -> state.statusMessage
        is DashboardViewState.BenchmarkResults -> "Benchmark Complete"
    }

    val currentMetricsContent = when (val state = uiState) {
        is DashboardViewState.ActiveJob -> state.metricsPanel
        is DashboardViewState.CompletedJob -> state.metricsPanel
        is DashboardViewState.PipelineFailure -> "Environment Failure:\n${state.installHint}"
        else -> "System Offline - Transitioning to Idle state."
    }

    val currentGpuContent = when (val state = uiState) {
        is DashboardViewState.CompletedJob -> state.gpuPanel
        is DashboardViewState.ActiveJob -> state.gpuPanel
        is DashboardViewState.PipelineFailure -> "Diagnostics:\n${state.installHint}"
        else -> "System Offline - Transitioning to Idle state."
    }

    val currentEssayContent = when (val state = uiState) {
        is DashboardViewState.ActiveJob -> state.essayText
        is DashboardViewState.CompletedJob -> state.essayText
        else -> ""
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.startPipeline(onEssayChunkReceived = { chunk -> print(chunk) }) },
                enabled = uiState is DashboardViewState.Idle,
            ) {
                Text("Start Pipeline")
            }
            Button(
                onClick = { viewModel.stopPipeline() },
                enabled = uiState is DashboardViewState.ActiveJob,
            ) {
                Text("Stop Pipeline")
            }
            Button(
                onClick = { viewModel.runBenchmark() },
                enabled = uiState is DashboardViewState.Idle,
            ) {
                Text("Run Benchmark")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState is DashboardViewState.BenchmarkResults) {
            BenchmarkReportView((uiState as DashboardViewState.BenchmarkResults).report)
        } else {
            if (uiState is DashboardViewState.PipelineFailure.RateLimited) {
                val rateLimitState = uiState as DashboardViewState.PipelineFailure.RateLimited
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Groq API Rate Limited",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = rateLimitState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Retry after: ${rateLimitState.retryAfterSeconds}s | The benchmark will not count results until this resets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(text = "Research Monitor:", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = currentStatusMessage, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShellPanel(
                    title = "Metrics",
                    content = currentMetricsContent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                ShellPanel(
                    title = "GPU / System",
                    content = currentGpuContent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            EssayPanel(
                title = "Essay Draft",
                content = currentEssayContent,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}