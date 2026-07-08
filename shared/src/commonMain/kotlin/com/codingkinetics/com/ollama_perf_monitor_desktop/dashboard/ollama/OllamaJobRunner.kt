package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaJobResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

/**
 * Platform-specific driver for the Ollama runtime: it launches the `ollama serve` process,
 * streams a generation job over the local HTTP API, and releases the server process.
 *
 * Implementations are JVM/desktop-only and depend on the `ollama` executable being available on
 * the PATH. [startOllamaServer] must be called (and must become ready) before
 * [runOllamaEssayJob]; [cleanupRuntimeResources] should always be invoked afterwards to kill the
 * server process and any stray `ollama`/`llama-server` processes.
 */
interface OllamaJobRunner {

    /** Starts the Ollama server subprocess and blocks until it accepts connections. */
    fun startOllamaServer()

    /**
     * Runs a single streaming generation job against the running Ollama server.
     *
     * @param model Ollama model name to generate with (i.e. `llama3.2`).
     * @param prompt input prompt for the model.
     * @param onChunk invoked with each streamed text delta as it arrives.
     * @param onTokenProgress invoked with cumulative prompt-eval and generation token counts.
     * @return the full [OllamaJobResult], or [Result.Failure] if the server is unreachable or the
     *   request fails. The network call runs on the IO dispatcher.
     */
    suspend fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onTokenProgress: (promptEvalCount: Long, evalCount: Long) -> Unit = { _, _ -> },
    ): Result<OllamaJobResult>

    /** Kills the Ollama server process and any related subprocesses. */
    fun cleanupRuntimeResources()
}