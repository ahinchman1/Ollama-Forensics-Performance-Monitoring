package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.BtopMetricsCollectorImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.RagasEngine
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewModel
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobRunnerImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.ComposeContextContentProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.DashboardView
@Composable
fun App() {
    MaterialTheme {
        val appScope = rememberCoroutineScope()
        val contentProvider = ComposeContextContentProvider()

        val orchestrator = remember {
            OllamaJobOrchestrator(
                jobRunner = OllamaJobRunnerImpl(),
                btopMetrics = BtopMetricsCollectorImpl(),
                ragasEngine = RagasEngine(loadContexts = { contentProvider.loadContexts() }),
            )
        }

        val viewModel = remember { DashboardViewModel(appScope, orchestrator) }


        DisposableEffect(Unit) {
            onDispose {
                viewModel.clearRuntimeResources()
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DashboardView(viewModel)
        }
    }
}
