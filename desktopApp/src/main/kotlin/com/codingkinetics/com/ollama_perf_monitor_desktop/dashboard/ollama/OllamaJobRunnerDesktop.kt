package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaJobResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaStreamChunk
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProvider
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.ollamaExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.runCommandIgnoringErrors
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.withCliPath
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
private data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
)

class OllamaJobRunnerDesktop(
    private val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProviderImpl(),
): OllamaJobRunner {

    private var serverProcess: Process? = null

    private val jsonWorker = Json {
        ignoreUnknownKeys = true
    }

    private val ollamaBaseUrl = "http://127.0.0.1:11434"
    private val ollamaStreamingEndpointUrl = URL("$ollamaBaseUrl/api/generate")

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

    override suspend fun runOllamaEssayJob(
        model: String,
        prompt: String,
        onChunk: (String) -> Unit,
        coroutineContextProvider: CoroutineContextProvider,
    ): Result<OllamaJobResult> {
        var connection: HttpURLConnection? = null
        return try {
            connection = withContext(coroutineContextProvider.ioDispatcher) {
                ollamaStreamingEndpointUrl.openConnection()
            } as HttpURLConnection

            withContext(coroutineContextProvider.ioDispatcher) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 60000

                connection.setChunkedStreamingMode(0)

                val request = OllamaGenerateRequest(model = model, prompt = prompt, stream = true)
                val jsonPayload = jsonWorker.encodeToString(request)

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown Error"
                    error("Ollama REST API returned HTTP $responseCode: $errorText")
                }

                var finalResultData: OllamaJobResult? = null

                streamRawJsonChunks(connection, onChunk) { output, completedData ->
                    finalResultData = OllamaJobResult(generatedText = output, completedData = completedData)
                }

                finalResultData?.let { result ->
                    println("Generated Text:\n${result.generatedText}")
                    writeOutputToTmp(result.generatedText)
                }

                finalResultData
            }?.let {
                Result.Success(it)
            } ?: Result.Failure(
                IllegalStateException("Stream finished without generating completion records."),
            )
        } catch (e: Exception) {
            println("Error running Ollama job: ${e.message}")
            Result.Failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun streamRawJsonChunks(
        connection: HttpURLConnection,
        onChunk: (String) -> Unit,
        onAiJobComplete: (String, OllamaResponseCompletedData) -> Unit,
    ) {
        val output = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: break
                if (currentLine.isBlank()) continue

                runCatching {
                    val chunk = jsonWorker.decodeFromString<OllamaStreamChunk>(currentLine)
                    if (chunk.response.isNotEmpty()) {
                        onChunk(chunk.response)
                        output.append(chunk.response)
                    }

                    if (chunk.done) {
                        val outputStr = output.toString()
                        val finalData = jsonWorker.decodeFromString<OllamaResponseCompletedData>(currentLine)
                        onAiJobComplete(outputStr, finalData)
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

    private fun writeOutputToTmp(text: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(System.getProperty("java.io.tmpdir"), "ollama_output_$timestamp.txt")
        outputFile.writeText(text)
        println("Output written to: ${outputFile.absolutePath}")
    }
}
