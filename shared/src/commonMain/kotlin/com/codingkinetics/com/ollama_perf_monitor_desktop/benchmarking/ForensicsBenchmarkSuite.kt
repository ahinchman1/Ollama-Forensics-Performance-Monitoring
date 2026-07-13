package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.TokenTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.ScenarioTimeSeries
import com.codingkinetics.com.ollama_perf_monitor_desktop.di.OLLAMA_MODEL
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.resolveOllamaRuntimePids
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs the configured Ollama benchmark scenarios sequentially and produces an aggregate
 * [BenchmarkSuiteReport].
 *
 * This is the default [BenchmarkRunner] implementation. For each scenario it starts the Ollama
 * server + tmux dashboard (via [orchestrator]), runs the generation job, samples OS telemetry,
 * and runs the forensic evaluation, then releases runtime resources in a `finally` block so the
 * server process and tmux session are cleaned up even on failure. If [outputDir] is provided,
 * the Markdown report is written there on the IO dispatcher.
 *
 * @param orchestrator owns the Ollama server, metrics dashboard, and evaluation lifecycle.
 * @param model Ollama model used for every scenario.
 */
class ForensicsBenchmarkSuite(
    private val orchestrator: OllamaJobOrchestrator,
    private val model: String = OLLAMA_MODEL,
    private val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProviderImpl(),
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
        logFile: File? = null,
    ): BenchmarkSuiteReport {
        val logWriter = logFile?.also { it.parentFile?.mkdirs() }?.bufferedWriter()

        suspend fun log(message: String) {
            println(message)
            logWriter?.appendLine(message)
            withContext(coroutineContextProvider.ioDispatcher) {
                logWriter?.flush()
            }
        }

        logBenchmarkDebuggingGuide { message -> log(message) }

        orchestrator.startServer()
        logResolvedOllamaPids("After server start", ::log)
        orchestrator.startDashboard()

        val results = mutableListOf<BenchmarkScenarioResult>()

        try {
            for (scenario in scenarios) {
                log("\n${"=".repeat(60)}")
                log("Running: ${scenario.name} (${scenario.id})")
                log("Prompt: ${scenario.prompt.take(80)}...")
                log("=".repeat(60))
                logResolvedOllamaPids("Before scenario ${scenario.id}", ::log)
                onProgress("Running: ${scenario.name} (${scenario.id})")

                val result = runScenario(scenario, onProgress, onChunk, ::log)
                result?.let {
                    results.add(it)
                    val status = "Completed: ${scenario.name}, Hallucination Index: ${it.hallucinationIndex}"
                    log(status)
                    logResolvedOllamaPids("After scenario ${scenario.id}", ::log)
                    onProgress(status)
                }
            }

            return BenchmarkSuiteReport(results, formatDate(Date())).also { report ->
                outputDir?.let { writeReport(report, it) }
                logOllamaServerLog { message -> log(message) }
                log("Benchmark suite completed at ${Date()}")
            }
        } finally {
            logWriter?.close()
            orchestrator.cleanupRuntimeResources()
        }
    }

    private suspend fun logBenchmarkDebuggingGuide(log: suspend (String) -> Unit) {
        log(
            """
            Go Goroutine Profiles: Execute a pprof dump (/debug/pprof/goroutine?debug=2) during repeated non-streaming timeout testing to monitor for stabilizing goroutine counts.
            """.trimIndent()
        )
        log(
            """
            Yes. The most useful "one command" is to run the Ollama server in the foreground with debug/trace logging and tee it to a file:

            OLLAMA_DEBUG=2 OLLAMA_DEBUG_LOG_REQUESTS=1 ollama serve 2>&1 | tee ollama-debug.log

            That gives you:

            - OLLAMA_DEBUG=2 → trace-level server logging.
            - OLLAMA_DEBUG_LOG_REQUESTS=1 → logs inference request bodies/replay info, if supported by your build.
            - tee ollama-debug.log → keeps logs visible and writes them to disk.

            If you only want debug, not trace:

            OLLAMA_DEBUG=1 OLLAMA_DEBUG_LOG_REQUESTS=1 ollama serve 2>&1 | tee ollama-debug.log

            ## To capture goroutine/thread evidence while it is running

            In another terminal, get the PID:

            pgrep -x ollama

            Then collect Go runtime profiles from the built-in pprof endpoint:

            mkdir -p ollama-profile

            curl -s "http://127.0.0.1:11434/debug/pprof/goroutine?debug=2" > ollama-profile/goroutines.txt
            curl -s "http://127.0.0.1:11434/debug/pprof/threadcreate?debug=2" > ollama-profile/threadcreate.txt
            curl -s "http://127.0.0.1:11434/debug/pprof/heap?debug=1" > ollama-profile/heap.txt
            curl -s "http://127.0.0.1:11434/debug/pprof/profile?seconds=30" > ollama-profile/cpu.pprof

            For the suspected channel/goroutine leak, the most important file is:

            ollama-profile/goroutines.txt

            Look for stacks containing things like:

            chan send
            GenerateHandler
            ChatHandler
            Completion

            ## macOS native thread snapshot

            If you are on macOS and want the native thread picture too:

            PID="${'$'}(pgrep -x ollama | head -n1)"
            sample "${'$'}PID" 10 -file ollama-profile/sample.txt

            You can also capture a quick process/thread summary:

            PID="${'$'}(pgrep -x ollama | head -n1)"
            ps -M "${'$'}PID" > ollama-profile/threads.txt

            ## A practical all-in-one capture command

            Run this while your benchmark is reproducing the issue:

            mkdir -p ollama-profile && \
            PID="${'$'}(pgrep -x ollama | head -n1)" && \
            date > ollama-profile/timestamp.txt && \
            curl -s "http://127.0.0.1:11434/debug/pprof/goroutine?debug=2" > ollama-profile/goroutines.txt && \
            curl -s "http://127.0.0.1:11434/debug/pprof/threadcreate?debug=2" > ollama-profile/threadcreate.txt && \
            curl -s "http://127.0.0.1:11434/debug/pprof/heap?debug=1" > ollama-profile/heap.txt && \
            ps -M "${'$'}PID" > ollama-profile/threads.txt && \
            sample "${'$'}PID" 10 -file ollama-profile/sample.txt

            ## If you already added custom logs

            Start Ollama like this so your logs actually show up:

            OLLAMA_DEBUG=2 ollama serve 2>&1 | tee ollama-debug.log

            If your logs use slog.Debug, OLLAMA_DEBUG=1 is enough. If they use trace-style levels, use:

            OLLAMA_DEBUG=2

            ## What to look for

            Strong evidence of the channel leak would be many repeated goroutine stacks stuck around:

            chan send

            especially inside request handlers or completion callbacks.

            Strong evidence of native threadpool waiting would be lots of native stacks around:

            _pthread_cond_wait
            ggml
            llama
            threadpool

            That may indicate idle backend worker threads, not necessarily a Go channel leak. The goroutine dump is the key piece for proving the Go-side leak.
            """.trimIndent()
        )
    }

    private suspend fun logOllamaServerLog(log: suspend (String) -> Unit) {
        val serverLog = File(orchestrator.getServerLogPath())
        if (!serverLog.exists()) {
            log("Ollama server log not found at: ${serverLog.absolutePath}")
            return
        }
        log("===== Ollama Server Debug Log (${serverLog.absolutePath}) =====")
        runCatching {
            serverLog.readLines().takeLast(400).forEach { log(it) }
        }.onFailure {
            log("Unable to read Ollama server log: ${it.message}")
        }
    }

    private suspend fun runScenario(
        scenario: BenchmarkScenario,
        onProgress: suspend (String) -> Unit,
        onChunk: (String) -> Unit,
        log: suspend (String) -> Unit = {},
    ): BenchmarkScenarioResult? {
        log("--- Scenario: ${scenario.name} ---")
        log("Prompt: ${scenario.prompt}")

        orchestrator.resetTimeSeriesSnapshots()

        val tokenSnapshots = mutableListOf<TokenTimeSeriesSnapshot>()
        val tokenProgressCallback: (Long, Long) -> Unit = { promptEvalCount, evalCount ->
            tokenSnapshots.add(
                TokenTimeSeriesSnapshot(
                    timestampMillis = System.currentTimeMillis(),
                    cumulativePromptTokens = promptEvalCount,
                    cumulativeGeneratedTokens = evalCount,
                )
            )
        }

        return when (val result = orchestrator.runOllamaEssayJob(
            model = model,
            prompt = scenario.prompt,
            onChunk = onChunk,
            onTokenProgress = tokenProgressCallback,
        )) {
            is Result.Success -> {
                val response = result.data.output
                log("Response: ${response.take(500)}${if (response.length > 500) "..." else ""}")
                log("Forensics:")
                log("- Hallucination Index : ${result.data.hallucinationIndex}")
                log("- Faithfulness Score  : ${result.data.faithfulnessScore}")
                log("- Prompt Tokens       : ${result.data.promptTokensCount}")
                log("- Generated Tokens    : ${result.data.generatedTokensCount}")
                log("- Total Duration (s)  : ${"%.1f".format(result.data.totalDurationNanos / 1_000_000_000.0)}")
                log("- Generation Speed    : ${"%.2f".format(result.data.tokensPerSecond)} t/s")
                log("- Ingestion Speed     : ${"%.2f".format(result.data.promptIngestionSpeed)} t/s")
                log("- Aggregate CPU Load  : ${String.format("%.1f", result.data.osMetrics.aggregateCpuConsumption)}%")
                log("- Peak Thread Count   : ${result.data.osMetrics.threadCount}")
                val cpuSnapshots = orchestrator.getCpuTimeSeriesSnapshots()

                tokenSnapshots.add(
                    TokenTimeSeriesSnapshot(
                        timestampMillis = System.currentTimeMillis(),
                        cumulativePromptTokens = result.data.promptTokensCount,
                        cumulativeGeneratedTokens = result.data.generatedTokensCount,
                    )
                )

                toBenchmarkResult(result.data, scenario, cpuSnapshots, tokenSnapshots)
            }
            is Result.Failure -> {
                val msg = "Scenario ${scenario.id} failed: ${result.exception.message}"
                log(msg)
                onProgress(msg)
                null
            }
        }
    }

    private suspend fun logResolvedOllamaPids(
        label: String,
        log: (suspend (String) -> Unit)?,
    ) {
        val pids = resolveOllamaRuntimePids()
        val message = if (pids.isEmpty()) {
            "$label - Ollama runtime PID(s): none resolved"
        } else {
            "$label - Ollama runtime PID(s): ${pids.joinToString(", ")}"
        }

        if (log != null) {
            log(message)
        } else {
            println(message)
        }
    }

    private fun toBenchmarkResult(
        metrics: PerformanceMetrics,
        scenario: BenchmarkScenario,
        cpuSnapshots: List<CpuTimeSeriesSnapshot>,
        tokenSnapshots: List<TokenTimeSeriesSnapshot>,
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
        runtimePids = resolveOllamaRuntimePids(),
        timeSeries = ScenarioTimeSeries(
            cpuSnapshots = cpuSnapshots,
            tokenSnapshots = tokenSnapshots,
            scenarioId = scenario.id,
        ),
    )

    /**
     * Exports the benchmark report to `<outputDir>/baseline_run_<timestamp>.md`.
     * The file write runs on the IO dispatcher.
     */
    private suspend fun writeReport(report: BenchmarkSuiteReport, outputDir: File) {
        val filename = "baseline_run_${report.timestamp}.md"
        val file = File(outputDir, filename)

        withContext(coroutineContextProvider.ioDispatcher) {
            file.writeText(report.toMarkdown())
        }
        println("Report written to: ${file.absolutePath}")
    }

    private fun formatDate(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

/**
 * Definition of a single benchmark scenario.
 *
 * @param id stable scenario identifier (i.e. `01`), used in report labels.
 * @param name human-readable scenario name.
 * @param prompt prompt sent to the model for this scenario.
 * @param description what the scenario stresses (used for reporting/context).
 */
data class BenchmarkScenario(
    val id: String,
    val name: String,
    val prompt: String,
    val description: String,
)
