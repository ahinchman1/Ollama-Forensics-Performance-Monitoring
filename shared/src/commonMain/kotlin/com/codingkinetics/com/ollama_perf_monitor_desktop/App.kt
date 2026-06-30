package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewModel
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.DashboardView
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val client = HttpClient(CIO)

val jsonClient = client.config {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            },
        )
    }
}

@Composable
fun App(ollamaJobOrchestrator: OllamaJobOrchestrator) {
    MaterialTheme {
        val appScope = rememberCoroutineScope()
        val orchestrator = remember { ollamaJobOrchestrator }
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
