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
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.models.DashboardViewState
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DashboardView(viewModel: DashboardViewModel) {
    val uiState by viewModel.viewState.collectAsState()

    LaunchedEffect(uiState is DashboardViewState.ActiveJob) {
        while (isActive && uiState is DashboardViewState.ActiveJob) {
            viewModel.synchronousRefresh()
            delay(1000)
        }
    }

    val currentStatusMessage = when (val state = uiState) {
        is DashboardViewState.Idle -> "System Ready. Infrastructure offline."
        is DashboardViewState.ActiveJob -> state.statusMessage
        is DashboardViewState.Error -> "FAILURE: ${state.errorMessage}"
        is DashboardViewState.CompletedJob -> state.statusMessage
    }

    val currentMetricsContent = when (val state = uiState) {
        is DashboardViewState.ActiveJob -> state.metricsPanel
        is DashboardViewState.CompletedJob -> state.metricsPanel
        is DashboardViewState.Error -> "Environment Failure:\ntmux target broken or crashed."
        else -> "System Offline - Transitioning to Idle state."
    }

    val currentGpuContent = when (val state = uiState) {
        is DashboardViewState.CompletedJob -> {
            println("Completed job data: ${state.completedData}")
            state.completedData.toString()
        }
        is DashboardViewState.ActiveJob -> state.gpuPanel
        is DashboardViewState.Error -> "Diagnostics:\ntmux: ${state.tmuxPath}\nollama: ${state.ollamaPath}"
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Research Monitor:", style = MaterialTheme.typography.labelMedium)
        Text(
            text = currentStatusMessage,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

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