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

        val groqApiKey = System.getenv("GROQ_API_KEY")
        val evaluator = groqApiKey?.let { ForensicsEvaluator(appSdkResources.httpClient, it) }

        val orchestrator = OllamaJobOrchestrator(
            jobRunner = OllamaJobRunnerDesktop(),
            metricsCollector = BtopMetricsCollector(),
            ragasEngine = RagasEngine(
                forensicsEvaluator = evaluator,
                loadContexts = { contentProvider.loadContexts() },
            ),
        )

        App(orchestrator, appSdkResources)
    }
}