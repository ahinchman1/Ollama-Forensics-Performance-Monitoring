package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.models.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.models.OllamaStreamChunk
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.btopExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.ollamaExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.resolveExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.withCliPath
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class OllamaJobOrchestrator {

    private var serverProcess: Process? = null
    private val jsonWorker = Json {
        ignoreUnknownKeys = true
    }

    private val ollamaBaseUrl = "http://127.0.0.1:11434"

    internal fun checkMonitoringToolDependency(): Result<Unit>  {
        return when {
            !commandExists("tmux") ->
                Result.Failure(IllegalStateException("tmux is required but was not found."))
            !commandExists("btop") ->
                Result.Failure(IllegalStateException("btop is required but was not found. Install it with Homebrew or your package manager."))
            !commandExists("ollama") ->
                Result.Failure(IllegalStateException("ollama is required but was not found. Install it with Homebrew or your package manager."))
            else -> Result.Success(Unit)
        }
    }

    internal fun startOllamaServer() {
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
            waitForOllamaServer()

        } catch (e: Exception) {
            throw IllegalStateException(
                "Process Builder failed to execute the ollama binary at '$ollamaExecutable'. " +
                        "System error: ${e.message}",
                e,
            )
        }
    }

    private fun waitForOllamaServer(timeoutMillis: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError: Exception? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val connection = URL("$ollamaBaseUrl/").openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 500
                connection.readTimeout = 500

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode in 200..499) {
                    return
                }
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(250)
            }
        }

        throw IllegalStateException(
            "Ollama server did not become ready at $ollamaBaseUrl within ${timeoutMillis}ms. " +
                    "Last error: ${lastError?.message}"
        )
    }

    internal fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onAiJobComplete: (OllamaResponseCompletedData) -> Unit = {},
    ): Result<Unit> = try {
        val ollamaStreamingEndpointUrl = URL("$ollamaBaseUrl/api/generate")
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
          "model": "$model",
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

        streamRawJsonChunks(connection, onChunk) { completedData ->
            onAiJobComplete(completedData)
        }
        connection.disconnect()
        Result.Success(Unit)
    } catch (e: Exception) {
        println("Error running Ollama job: ${e.message}")
        Result.Failure(e)
    }

    internal fun startTmuxDashboard() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)

        try {
            val initProcess = ProcessBuilder(
                tmuxExecutable,
                "new-session",
                "-d",
                "-s",
                tmuxSessionName,
                btopExecutable,
            ).withCliPath().start()

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
                "-p", "65", // takes 65 percent of the window
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

    internal fun captureTmuxPane(targetPane: String): String {
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

    internal fun commandExists(command: String): Boolean {
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

    internal fun runCommandIgnoringErrors(vararg command: String) {
        runCatching {
            ProcessBuilder(*command).withCliPath().start().waitFor(500, TimeUnit.MILLISECONDS)
        }.onFailure { e ->
            println("Ignoring error running command: ${command.joinToString(" ")}. Cause: ${e.message}")
        }
    }

    private fun gpuMonitoringCommand(): String {
        return when {
            commandExists("vcgencmd") -> "watch -n 1 'vcgencmd measure_temp && vcgencmd get_throttled'"
            commandExists("nvtop") -> "nvtop"
            else -> "top -l 1 | head -n 25"
        }
    }

    internal fun streamRawJsonChunks(
        connection: HttpURLConnection,
        onChunk: (String) -> Unit,
        onAiJobComplete: (OllamaResponseCompletedData) -> Unit = {}
    ) {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: break
                if (currentLine.isBlank()) continue

                runCatching {
                    val chunk = jsonWorker.decodeFromString<OllamaStreamChunk>(currentLine)
                    if (chunk.response.isNotEmpty()) {
                        onChunk(chunk.response)
                    }

                    if (chunk.done) {
                        val finalData = jsonWorker.decodeFromString<OllamaResponseCompletedData>(currentLine)
                        onAiJobComplete(finalData)
                    }
                }.onFailure { e ->
                    println("Skipping malformed or incomplete JSON frame: $currentLine. Error: ${e.message}")
                }
            }
        }
    }

    internal fun cleanupRuntimeResources() {
        runCommandIgnoringErrors(tmuxExecutable, "kill-session", "-t", tmuxSessionName)
        serverProcess?.destroyForcibly()
        serverProcess = null
    }
}
