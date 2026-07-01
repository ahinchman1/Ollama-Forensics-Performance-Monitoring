package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCatchingDomain
import ollama_perf_monitor_desktop.shared.generated.resources.Res

interface ContextContentProvider {
    suspend fun loadContexts(): Result<List<String>>
}

class ComposeContextContentProvider: ContextContentProvider {
    private val contextRegistry = mapOf(
        "arxiv" to listOf("latency_and_token_aware_test_time_compute.txt"),
        "java" to listOf(
            "chapter_17_threads_and_locks.txt",
            "CompletableFuture.txt",
            "Future.txt",
            "ThreadPoolExecutor.txt",
        ),
        "kotlin" to listOf("asynchronous_programming_techniques.txt", "coroutines.txt"),
        "linux_kernel" to listOf(
            "CPU_load.txt",
            "energy_model.txt",
            "locktypes.txt",
            "scheduler.txt",
            "wound_wait_deadlock_proof_mutex_design.txt"
        ),
        "wikipedia" to listOf(
            "concurrency_computer_science.txt",
            "concurrency_control.txt",
            "indeterminacy_in_concurrent_computation.txt"
        )
    )
    override suspend fun loadContexts(): Result<List<String>> = runCatchingDomain {
        contextRegistry.flatMap { (domain, files) ->
            files.map { fileName ->
                val path = "files/$domain/$fileName"
                val bytes = Res.readBytes(path)
                "SOURCE: $domain/$fileName\n${bytes.decodeToString().trim()}"
            }
        }
    }
}