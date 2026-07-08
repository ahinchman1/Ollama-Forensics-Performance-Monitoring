package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.DashboardView
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewModel
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.AppSdkResources

@Composable
fun App(
    ollamaJobOrchestrator: OllamaJobOrchestrator,
    appSdkResources: AppSdkResources,
) {
    OllamaForensicsTheme {
        val appScope = rememberCoroutineScope()
        val orchestrator = remember { ollamaJobOrchestrator }
        val viewModel = remember { DashboardViewModel(appScope, orchestrator) }

        DisposableEffect(Unit) {
            onDispose {
                viewModel.clearRuntimeResources()
                appSdkResources.close()
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
