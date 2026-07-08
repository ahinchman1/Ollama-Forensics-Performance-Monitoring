package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Top-level owner for process-external SDK resources used by the desktop app.
 *
 * Owns the single [HttpClient] shared across the application so its engine,
 * connection pools, and dispatcher threads are released deterministically on exit.
 * Call [close] from the application lifecycle (e.g. on window dispose) to avoid
 * leaking sockets and background threads.
 */
class AppSdkResources : AutoCloseable {

    val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                },
            )
        }
    }

    override fun close() {
        httpClient.close()
    }
}
