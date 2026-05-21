package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.controller.DashboardController
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.DashboardView
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.EssayPanel
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.ShellPanel
import kotlinx.coroutines.*

@Composable
fun App() {
    MaterialTheme {
        val appScope = rememberCoroutineScope()
        val viewModel = remember { DashboardController(appScope) }

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
