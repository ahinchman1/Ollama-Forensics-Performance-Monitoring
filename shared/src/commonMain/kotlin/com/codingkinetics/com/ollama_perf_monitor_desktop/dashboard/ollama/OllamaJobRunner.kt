package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaStreamChunk
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollamaExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.withCliPath
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

interface OllamaJobRunner {

    fun startOllamaServer()

    fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onAiJobComplete: (OllamaResponseCompletedData) -> Unit = {},
    ): Result<Unit>

    fun cleanupRuntimeResources()
}

class OllamaJobRunnerImpl(): OllamaJobRunner {

    private var serverProcess: Process? = null

    private val jsonWorker = Json {
        ignoreUnknownKeys = true
    }

    private val ollamaBaseUrl = "http://127.0.0.1:11434"

    override fun startOllamaServer() = try {
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
                "Process Builder failed to execute the ollama binary at '${ollamaExecutable}'. " +
                        "System error: ${e.message}",
                e,
            )
        }

    private fun waitForOllamaServer(timeoutMillis: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError: Exception? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val connection = URI.create("$ollamaBaseUrl/").toURL().openConnection() as HttpURLConnection
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

    override fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        onAiJobComplete: (OllamaResponseCompletedData) -> Unit,
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

    override fun cleanupRuntimeResources() {
        serverProcess?.destroyForcibly()
        serverProcess = null
    }
}
