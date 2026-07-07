package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

interface BenchmarkRunner {
    suspend fun runBenchmarkSuite(): Result<BenchmarkSuiteReport>
}