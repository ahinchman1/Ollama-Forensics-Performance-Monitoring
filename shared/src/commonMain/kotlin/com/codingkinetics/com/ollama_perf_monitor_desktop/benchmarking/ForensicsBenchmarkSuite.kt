package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForensicsBenchmarkSuite(
    private val orchestrator: OllamaJobOrchestrator,
    private val model: String = "llama3.2",
) {

    private val scenarios = listOf(
        BenchmarkScenario(
            id = "01",
            name = "Baseline Control",
            prompt = "Explain the concept of token generation in large language models. " +
                    "Keep your response under 200 words and include at least one concrete example.",
            description = "Standard text generation without strict performance constraints.",
        ),
        BenchmarkScenario(
            id = "02",
            name = "Adversarial Math",
            prompt = "Calculate the total CPU percentage across all cores on my machine and report it numerically. " +
                    "State the exact value as a percentage between 0-100. Your answer must be a single " +
                    "number representing the total aggregate CPU utilization.",
            description = "Forcing explicit numeric telemetry claims inside the text output.",
        ),
        BenchmarkScenario(
            id = "03",
            name = "Concurrency Stress",
            prompt = "Write a complex Java transaction manager that handles concurrent deposits and withdrawals. " +
                    "Do not use the words: synchronized, volatile, Lock, ReentrantLock, Atomic, BlockingQueue, " +
                    "Semaphore, or any core Java concurrency utilities. Implement thread safety using " +
                    "alternative techniques.",
            description = "Instruction-following under high prompt cognitive load with token restrictions.",
        ),
    )

    suspend fun runSuite(
        outputDir: File? = null,
        onProgress: suspend (String) -> Unit = {},
        onChunk: (String) -> Unit = {},
    ): BenchmarkSuiteReport {
        orchestrator.startServer()
        orchestrator.startDashboard()

        val results = mutableListOf<BenchmarkScenarioResult>()

        try {
            for (scenario in scenarios) {
                onProgress("Running: ${scenario.name} (${scenario.id})")
                println("\n${"=".repeat(60)}")
                println("Running: ${scenario.name} (${scenario.id})")
                println("Prompt: ${scenario.prompt.take(80)}...")
                println("${"=".repeat(60)}\n")

                val result = runScenario(scenario, onProgress, onChunk)
                result?.let {
                    results.add(it)
                    val status = "Completed: ${scenario.name}, Hallucination Index: ${it.hallucinationIndex}"
                    onProgress(status)
                    println(status)
                }
            }

            return BenchmarkSuiteReport(results, formatDate(Date())).also { report ->
                outputDir?.let { writeReport(report, it) }
            }
        } finally {
            orchestrator.cleanupRuntimeResources()
        }
    }

    private suspend fun runScenario(
        scenario: BenchmarkScenario,
        onProgress: suspend (String) -> Unit,
        onChunk: (String) -> Unit,
    ): BenchmarkScenarioResult? {
        onProgress("--- Scenario: ${scenario.name} ---")
        onProgress("Prompt: ${scenario.prompt}")

        return when (val result = orchestrator.runOllamaEssayJob(model, scenario.prompt) { chunk ->
            onChunk(chunk)
        }) {
            is Result.Success -> {
                val response = result.data.output
                onProgress("Response: ${response.take(500)}${if (response.length > 500) "..." else ""}")
                toBenchmarkResult(result.data, scenario)
            }
            is Result.Failure -> {
                onProgress("Scenario ${scenario.id} failed: ${result.exception.message}")
                null
            }
        }
    }

    private fun toBenchmarkResult(
        metrics: PerformanceMetrics,
        scenario: BenchmarkScenario,
    ): BenchmarkScenarioResult = BenchmarkScenarioResult(
        scenarioId = scenario.id,
        scenarioName = scenario.name,
        prompt = scenario.prompt,
        response = metrics.output,
        generatedTokens = metrics.generatedTokensCount,
        promptTokens = metrics.promptTokensCount,
        totalDurationNanos = metrics.totalDurationNanos,
        generationDurationNanos = metrics.generationDurationNanos,
        loadDurationNanos = metrics.loadDurationNanos,
        promptIngestionSpeed = metrics.promptIngestionSpeed,
        tokenGenerationSpeed = metrics.tokensPerSecond,
        hallucinationIndex = metrics.hallucinationIndex,
        faithfulnessScore = metrics.faithfulnessScore,
        timestamp = formatDate(Date()),
        osMetrics = metrics.osMetrics,
    )

    private suspend fun writeReport(report: BenchmarkSuiteReport, outputDir: File) {
        val filename = "baseline_run_${report.timestamp}.md"
        val file = File(outputDir, filename)

        withContext(Dispatchers.IO) {
            file.writeText(report.toMarkdown())
        }
        println("Report written to: ${file.absolutePath}")
    }

    private fun formatDate(date: Date): String = 
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

data class BenchmarkScenario(
    val id: String,
    val name: String,
    val prompt: String,
    val description: String,
)