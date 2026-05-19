package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@Composable
fun App() {
    MaterialTheme {
        val appScope = rememberCoroutineScope()
        val viewModel = remember { DashboardController(appScope) }

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

@Composable
fun DashboardView(viewModel: DashboardController) {
    var essayText by remember { mutableStateOf("") }
    
    LaunchedEffect(viewModel.isRunning) {
        while (isActive && viewModel.isRunning) {
            viewModel.synchronousRefresh()
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.startPipeline(
                onEssayChunkReceived = { chunk -> essayText += chunk }
            ) }, enabled = !viewModel.isRunning) {
                Text("Start Pipeline")
            }
            Button(onClick = { viewModel.stopPipeline() }, enabled = viewModel.isRunning) {
                Text("Stop Pipeline")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Research Monitor:", style = MaterialTheme.typography.labelMedium)
        Text(
            text = viewModel.currentMetrics,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShellPanel(
                title = "Metrics",
                content = viewModel.metricsPanel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            ShellPanel(
                title = "GPU / System",
                content = viewModel.gpuPanel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        EssayPanel(
            title = "Essay Draft",
            content = viewModel.essayText,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun EssayPanel(title: String, content: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content.ifBlank { "The essay will appear here once generation starts." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun ShellPanel(title: String, content: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content.ifBlank { "Waiting for shell output..." },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
        )
    }
}

class DashboardController(private val scope: CoroutineScope) {
    var isRunning by mutableStateOf(false)
    var currentMetrics by mutableStateOf("Idle Baseline")
    var metricsPanel by mutableStateOf("")
    var gpuPanel by mutableStateOf("")
    var essayText by mutableStateOf("")

    private var serverProcess: Process? = null
    private var observabilityJob: Job? = null

    private val tmuxSessionName = "research_center"
    private val tmuxExecutable = resolveExecutable("tmux")
    private val ollamaExecutable = resolveExecutable("ollama")
    private val btopExecutable = resolveExecutable("btop")
    private val ollamaModel = "llama3.2"

    fun startPipeline(onEssayChunkReceived: (String) -> Unit) {
        currentMetrics = "Starting dashboard..."
        metricsPanel = "Starting metrics panel..."
        gpuPanel = "Starting GPU / system panel..."
        essayText = "Preparing Ollama essay job..."

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ensureMonitoringTools()
                    startTmuxDashboard()
                    startOllamaServer()
                }

                isRunning = true
                currentMetrics = "Dashboard running. Ollama essay generation is starting."
                essayText = "Waiting for Ollama output..."

                synchronousRefresh()
                startObservabilityStream()

                withContext(Dispatchers.IO) {
                    val prompt = "Write an essay analyzing advanced JVM concurrency patterns and coroutine leak detection metrics."

                    runOllamaEssayJob(prompt) { chunk ->
                        scope.launch(Dispatchers.Main.immediate) {
                            if (essayText == "Waiting for Ollama pre-fill...") {
                                essayText = ""
                            }

                            essayText += chunk

                            onEssayChunkReceived(chunk)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupRuntimeResources()

                isRunning = false
                currentMetrics = "Pipeline Error: ${e.message ?: e::class.simpleName}"
                metricsPanel = """
                    Metrics panel failed to start.

                    tmux resolved to:
                    $tmuxExecutable

                    btop resolved to:
                    $btopExecutable
                """.trimIndent()
                gpuPanel = """
                    Initialization failed before monitoring stabilized.

                    Check whether tmux, btop, and ollama are installed and accessible.
                """.trimIndent()
                essayText = """
                    Ollama essay generation did not start.

                    ollama resolved to:
                    $ollamaExecutable
                """.trimIndent()
            }
        }
    }

    fun stopPipeline(updateStatus: Boolean = true) {
        scope.launch {
            cleanupRuntimeResources()
            isRunning = false
            metricsPanel = ""
            gpuPanel = ""

            if (updateStatus) {
                currentMetrics = "Terminated."
                essayText = ""
            }
        }
    }

    fun clearRuntimeResources() {
        stopPipeline(updateStatus = false)
    }

    suspend fun synchronousRefresh() {
        val metrics = withContext(Dispatchers.IO) {
            captureTmuxPane("$tmuxSessionName:0.0")
        }

        val gpu = withContext(Dispatchers.IO) {
            captureTmuxPane("$tmuxSessionName:0.1")
        }

        metricsPanel = metrics
        gpuPanel = gpu
    }

    fun refreshShellPanels() {
        scope.launch {
            synchronousRefresh()
        }
    }

    fun startObservabilityStream() {
        observabilityJob?.cancel()

        observabilityJob = scope.launch {
            essayText = ""
            currentMetrics = "Running Ollama model '$ollamaModel'..."

            val result = withContext(Dispatchers.IO) {
                runOllamaEssayJob(
                    prompt = """
                        Write a clear technical essay about JVM concurrency and local AI inference.

                        Focus on:
                        - why local inference is useful for experimentation
                        - how JVM concurrency affects responsiveness
                        - why separating monitoring panels from generated prose improves the UI
                        - how observability helps compare CPU, memory, GPU, and process behavior

                        Keep the tone practical, technical, and research-oriented.
                    """.trimIndent(),
                    onChunk = { chunk ->
                        // FIXED: Enforce safe Main-immediate dispatching to prevent text mutation race conditions
                        scope.launch(Dispatchers.Main.immediate) {
                            essayText += chunk
                        }
                    },
                )
            }

            if (result.isSuccess) {
                currentMetrics = "Ollama essay generation complete. Monitoring panels active."
            } else {
                currentMetrics = "Ollama essay generation failed."
                essayText = "Ollama failure: ${result.exceptionOrNull()?.message}"
            }
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
                "-h", // horizontal-split
                "-p", "35", // takes 35 percent of the window
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
            throw IllegalStateException("Process Builder failed to execute the ollama binary at '$ollamaExecutable'. System error: ${e.message}", e)
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
        // 1. Target Ollama's native streaming endpoint
        val url = URL("http://127.0.0.1:11434/api/generate")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 60000

        // 2. Escape the prompt safely into a minimal JSON payload
        // We explicitly escape newlines and quotes so the JSON serializer doesn't snap
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

        // 3. Fire the payload into the network socket
        connection.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown Error"
            error("Ollama REST API returned HTTP $responseCode: $errorText")
        }

        // 4. Stream the raw JSON chunks as they fly off the server wire
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: break
                if (currentLine.isBlank()) continue

                // Ollama streams lines of JSON objects looking like: {"response":"token","done":false}
                // A fast string extraction avoids bringing in heavy JSON parsing libraries
                if (currentLine.contains("\"response\":\"")) {
                    val token = currentLine.substringAfter("\"response\":\"").substringBefore("\",\"")
                    // Unescape basic string line breaks if the model sends them
                    val cleanToken = token.replace("\\n", "\n").replace("\\t", "\t")
                    onChunk(cleanToken)
                }
            }
        }

        connection.disconnect()
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
                "/bin/$command"
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