package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.BtopMetricsCollector
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobRunnerDesktop
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.ForensicsEvaluator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.RagasEngine
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.ComposeContextContentProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.AppSdkResources

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ollama_perf_monitor_desktop",
    ) {
        val appSdkResources = remember { AppSdkResources() }
        val contentProvider = ComposeContextContentProvider()

        val orchestrator = OllamaJobOrchestrator(
            jobRunner = OllamaJobRunnerDesktop(),
            metricsCollector = BtopMetricsCollector(),
            ragasEngine = RagasEngine(
                forensicsEvaluator = ForensicsEvaluator(appSdkResources.httpClient),
                loadContexts = { contentProvider.loadContexts() },
            ),
        )

        App(orchestrator, appSdkResources)
    }
}