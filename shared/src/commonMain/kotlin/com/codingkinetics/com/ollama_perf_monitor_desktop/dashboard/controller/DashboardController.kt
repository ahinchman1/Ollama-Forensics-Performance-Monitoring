package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.controller

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.models.DashboardViewState
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.models.OllamaStreamChunk
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DashboardController(
    private val scope: CoroutineScope,
    private val contextPool: CoroutineContextProvider = CoroutineContextProviderImpl(),
) {
    private val _viewState = MutableStateFlow<DashboardViewState>(DashboardViewState.Idle)
    val viewState: StateFlow<DashboardViewState> = _viewState.asStateFlow()

    private var serverProcess: Process? = null
    private var observabilityJob: Job? = null

    private val tmuxSessionName = "research_center"
    private val tmuxExecutable = resolveExecutable("tmux")
    private val ollamaExecutable = resolveExecutable("ollama")
    private val btopExecutable = resolveExecutable("btop")
    private val ollamaModel = "llama3.2"

    private val jsonWorker = Json {
        ignoreUnknownKeys = true
    }

    fun startPipeline(onEssayChunkReceived: (String) -> Unit) {
        // 1. Instantly transition UI out of Idle to display loading frameworks
        _viewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Starting dashboard...",
            metricsPanel = "Starting metrics panel...",
            gpuPanel = "Starting GPU / system panel...",
            essayText = "Preparing Ollama essay job..."
        )

        scope.launch(contextPool.mainImmediateDispatcher) {
            try {
                withContext(contextPool.ioDispatcher) {
                    ensureMonitoringTools()
                    startTmuxDashboard()
                    startOllamaServer()
                }

                _viewState.update { currentState ->
                    if (currentState is DashboardViewState.ActiveJob) {
                        currentState.copy(
                            statusMessage = "Dashboard running. Ollama essay generation is starting.",
                            essayText = "Waiting for Ollama pre-fill..."
                        )
                    } else currentState
                }

                synchronousRefresh()
                startObservabilityStream()

                withContext(contextPool.ioDispatcher) {
                    val prompt = """
                            Write a clear technical essay about JVM concurrency and local AI inference.
                            Focus on:
                                - why local inference is useful for experimentation
                                - how JVM concurrency affects responsiveness
                                - why separating monitoring panels from generated prose improves the UI
                                - how observability helps compare CPU, memory, GPU, and process behavior
    
                            Keep the tone practical, technical, and research-oriented.
                            """.trimIndent()

                    runOllamaEssayJob(prompt) { chunk ->
                        _viewState.update { currentState ->
                            if (currentState is DashboardViewState.ActiveJob) {
                                val cleanText = if (currentState.essayText == "Waiting for Ollama pre-fill...") {
                                    ""
                                } else {
                                    currentState.essayText
                                }
                                currentState.copy(essayText = cleanText + chunk)
                            } else currentState
                        }

                        onEssayChunkReceived(chunk)
                    }
                }

                updatePipelineStatus(isComplete = true)
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupRuntimeResources()

                updatePipelineStatus(isComplete = false, failureMessage = e.message ?: e::class.simpleName)
                _viewState.value = DashboardViewState.Error(
                    errorMessage = "Pipeline Error: ${e.message ?: e::class.simpleName}",
                    tmuxPath = tmuxExecutable,
                    btopPath = btopExecutable,
                    ollamaPath = ollamaExecutable
                )
            }
        }
    }

    fun stopPipeline() {
        scope.launch {
            cleanupRuntimeResources()
            _viewState.value = DashboardViewState.Idle
        }
    }

    fun clearRuntimeResources() {
        stopPipeline()
    }

    suspend fun synchronousRefresh() {
        val metrics = withContext(contextPool.ioDispatcher) {
            captureTmuxPane("$tmuxSessionName:0.0")
        }

        val gpu = withContext(contextPool.ioDispatcher) {
            captureTmuxPane("$tmuxSessionName:0.1")
        }

        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                currentState.copy(metricsPanel = metrics, gpuPanel = gpu)
            } else currentState
        }
    }

    fun startObservabilityStream() {
        observabilityJob?.cancel()

        observabilityJob = scope.launch(contextPool.mainImmediateDispatcher) {
            _viewState.update { currentState ->
                if (currentState is DashboardViewState.ActiveJob) {
                    currentState.copy(
                        statusMessage = "Running Ollama model '$ollamaModel'...",
                        essayText = "Waiting for Ollama pre-fill..."
                    )
                } else currentState
            }
        }
    }

    fun updatePipelineStatus(isComplete: Boolean, failureMessage: String? = null) {
        _viewState.update { currentState ->
            if (currentState is DashboardViewState.ActiveJob) {
                if (isComplete) {
                    currentState.copy(statusMessage = "Ollama essay generation complete. Monitoring panels active.")
                } else {
                    currentState.copy(
                        statusMessage = "Ollama essay generation failed.",
                        essayText = failureMessage ?: "Ollama pipeline failure encountered."
                    )
                }
            } else currentState
        }
    }

    private suspend fun cleanupRuntimeResources() {
        observabilityJob?.cancel()
        observabilityJob = null

        withContext(Dispatchers.IO) {
            runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
            serverProcess?.destroyForcibly()
            serverProcess = null
        }
    }

    private fun ensureMonitoringTools() {
        if (!commandExists("tmux")) {
            error("tmux is required but was not found. Checked PATH and explicit binary standard destinations.")
        }

        if (!commandExists("btop")) {
            error("btop is required but was not found. Install it with Homebrew or your package manager.")
        }

        if (!commandExists("ollama")) {
            error("ollama is required but was not found. Install Ollama or update the executable path.")
        }
    }

    private fun startTmuxDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)

        try {
            val initProcess = ProcessBuilder(tmuxExecutable, "new-session", "-d", "-s", tmuxSessionName, btopExecutable)
                .withCliPath()
                .start()

            val initExitCode = initProcess.waitFor()
            if (initExitCode != 0) {
                val errorText = initProcess.errorStream.bufferedReader().use { it.readText() }
                error("tmux new-session failed with code $initExitCode: $errorText")
            }

            Thread.sleep(300)

            ProcessBuilder(tmuxExecutable, "rename-window", "-t", "$tmuxSessionName:0", "Metrics")
                .withCliPath()
                .start()
                .waitFor()

            val splitResult = ProcessBuilder(
                tmuxExecutable,
                "split-window",
                "-h",       // horizontal-split
                "-p", "65", // takes 35 percent of the window
                "-t",
                "$tmuxSessionName:0",
                "bash",
                "-lc",
                gpuMonitoringCommand(),
            )
                .withCliPath()
                .start()

            val splitExitCode = splitResult.waitFor()
            if (splitExitCode != 0) {
                val errorText = splitResult.errorStream.bufferedReader().use { it.readText() }
                error("tmux split-window failed with code $splitExitCode: $errorText")
            }

            ProcessBuilder(tmuxExecutable, "select-layout", "-t", "$tmuxSessionName:0", "even-horizontal")
                .withCliPath()
                .start()
                .waitFor()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to configure tmux dashboard layout: ${e.message}", e)
        }
    }

    private fun startOllamaServer() {
        try {
            runCommandIgnoringErrors("pkill", "-f", "ollama serve")
            val userHome = System.getProperty("user.home")
            val logFile = File(userHome, "ollama_server.log")

            if (logFile.exists()) logFile.delete()

            println("Initializing Ollama Server via target: $ollamaExecutable")
            println("Directing runtime logs straight to absolute path: ${logFile.absolutePath}")

            val builder = ProcessBuilder(ollamaExecutable, "serve")
                .withCliPath()
                .redirectOutput(ProcessBuilder.Redirect.to(logFile))
                .redirectError(ProcessBuilder.Redirect.to(logFile))

            builder.environment()["HOME"] = userHome

            builder.directory(File(userHome))

            serverProcess = builder.start()

        } catch (e: Exception) {
            throw IllegalStateException(
                "Process Builder failed to execute the ollama binary at '$ollamaExecutable'. " +
                        "System error: ${e.message}",
                e,
            )
        }
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }

    private fun captureTmuxPane(targetPane: String): String {
        return try {
            val process = ProcessBuilder(tmuxExecutable, "capture-pane", "-p", "-t", targetPane, "-S", "-80")
                .withCliPath()
                .start()

            process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
        } catch (e: Exception) {
            println("Error capturing pane $targetPane: ${e.message}")
            "Pane error $targetPane: ${e.message}"
        }
    }

    private fun runOllamaEssayJob(
        prompt: String,
        onChunk: (String) -> Unit,
    ): Result<Unit> = runCatching {
        val ollamaStreamingEndpointUrl = URL("http://localhost:11434/api/generate")
        val connection = ollamaStreamingEndpointUrl.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 60000

        connection.setChunkedStreamingMode(0)

        val escapedPrompt = prompt.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val jsonPayload = """
        {
          "model": "$ollamaModel",
          "prompt": "$escapedPrompt",
          "stream": true
        }
        """.trimIndent()

        // Fire the payload into the network socket
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown Error"
            error("Ollama REST API returned HTTP $responseCode: $errorText")
        }

        connection.streamRawJsonChunks(onChunk)
        connection.disconnect()
    }

    private fun HttpURLConnection.streamRawJsonChunks(onChunk: (String) -> Unit) {
        this.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: break
                if (currentLine.isBlank()) continue

                runCatching {
                    val chunk = jsonWorker.decodeFromString<OllamaStreamChunk>(currentLine)
                    if (chunk.response.isNotEmpty()) {
                        onChunk(chunk.response)
                    }
                }.onFailure { e ->
                    println("Skipping malformed or incomplete JSON frame: $currentLine. Error: ${e.message}")
                }
            }
        }
    }

    private fun commandExists(command: String): Boolean {
        val executable = resolveExecutable(command)
        if (File(executable).isAbsolute && File(executable).exists()) {
            return true
        }

        return try {
            val process = ProcessBuilder("bash", "-c", "command -v $command")
                .withCliPath()
                .start()
            process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun runCommandIgnoringErrors(vararg command: String) {
        runCatching {
            ProcessBuilder(*command).withCliPath().start().waitFor(500, TimeUnit.MILLISECONDS)
        }.onFailure { e ->
            println("Ignoring error running command: ${command.joinToString(" ")}. Cause: ${e.message}")
        }
    }

    companion object {
        private fun ProcessBuilder.withCliPath(): ProcessBuilder {
            val env = this.environment()
            val systemPath = System.getenv("PATH") ?: "/usr/bin:/bin"
            val homebrewPaths = "/opt/homebrew/bin:/usr/local/bin"
            env["PATH"] = "$homebrewPaths:$systemPath"
            return this
        }

        private fun resolveExecutable(command: String): String {
            val targetPaths = listOf(
                "/opt/homebrew/bin/$command",
                "/usr/local/bin/$command",
                "/usr/bin/$command",
                "/bin/$command",
                "/Users/amandahinchman-dominguez/Documents/repos/ollama/$command",
            )
            val found = targetPaths.firstOrNull { File(it).exists() }
            if (found != null) return found

            return try {
                val process = ProcessBuilder("bash", "-lc", "which $command").start()
                val path = process.inputStream.bufferedReader().use { it.readText() }.trim()
                if (process.waitFor() == 0 && path.isNotBlank()) path else command
            } catch (_: Exception) {
                command
            }
        }
    }
}