package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

/**
 * Runs a predefined suite of Ollama benchmark scenarios and produces an aggregate report.
 *
 * Implementations are expected to own the full lifecycle of a benchmark run: they start the
 * Ollama server and metrics dashboard, stream model output, sample OS telemetry, run the
 * forensic (Ragas/Groq) evaluation, and release runtime resources (server process, tmux
 * session) when finished. Suspending callers should be on an appropriate dispatcher; IO and
 * process orchestration are performed internally.
 *
 * Failures (missing runtime tools, Ollama errors, Groq rate limits, evaluation failures) are
 * surfaced as [Result.Failure] rather than thrown, so callers can present a meaningful error
 * state without crashing the pipeline.
 */
interface BenchmarkRunner {
    suspend fun runBenchmarkSuite(): Result<BenchmarkSuiteReport>
}