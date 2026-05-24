package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.DashboardViewModel
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.DashboardView
@Composable
fun App(ollamaJobOrchestrator: OllamaJobOrchestrator = OllamaJobOrchestrator()) {
    MaterialTheme {
        val appScope = rememberCoroutineScope()
        val viewModel = remember { DashboardViewModel(appScope, ollamaJobOrchestrator) }

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
