package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import kotlinx.coroutines.runBlocking
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForensicsEvaluatorTest {

    private fun createMockClient(mockEngine: MockEngine) = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun `evaluateFaithfulness returns Success with correct scores when API responds normally`() = runBlocking {
        // GIVEN
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.groq.com/openai/v1/chat/completions", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)

            respond(
                content = ByteReadChannel("""{"faithfulnessScore": 0.85}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = createMockClient(mockEngine)
        val evaluator = ForensicsEvaluator(client)

        // WHEN
        val result = evaluator.evaluateFaithfulness(
            prompt = "What color is the sky?",
            context = "The sky is blue today.",
            response = "The sky is completely blue."
        )

        // THEN
        assertTrue(result is Result.Success)
        val data = result.data
        assertEquals(0.85, data.faithfulnessScore, 0.001)
        assertEquals(0.15, data.hallucinationIndex, 0.001) // 1.0 - 0.85
    }

    @Test
    fun `evaluateFaithfulness returns Failure immediately if response is blank`() = runBlocking {
        // GIVEN
        val mockEngine = MockEngine { respondBadRequest() }
        val client = createMockClient(mockEngine)
        val evaluator = ForensicsEvaluator(client)

        // WHEN
        val result = evaluator.evaluateFaithfulness(
            prompt = "Test prompt",
            context = "Test context",
            response = "   " // Blank response boundary
        )

        // THEN
        assertTrue(result is Result.Failure)
        val exception = result.exception
        assertEquals("No response to evaluate.", exception.message)
    }

    @Test
    fun `evaluateFaithfulness uses default score when json parsing fails to locate field`() = runBlocking {
        // GIVEN
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"bad_key": "broken json payload"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createMockClient(mockEngine)
        val evaluator = ForensicsEvaluator(client)

        // WHEN
        val result = evaluator.evaluateFaithfulness("p", "c", "r")

        // THEN
        assertTrue(result is Result.Success)
        val data = result.data
        assertEquals(1.0, data.faithfulnessScore)
        assertEquals(0.0, data.hallucinationIndex)
    }

    @Test
    fun `evaluateFaithfulness returns Failure when HTTP request throws an exception`() = runBlocking {
        // GIVEN
        val mockEngine = MockEngine { _ ->
            throw Exception("Network connection lost")
        }
        val client = createMockClient(mockEngine)
        val evaluator = ForensicsEvaluator(client)

        // WHEN
        val result = evaluator.evaluateFaithfulness("p", "c", "r")

        // THEN
        assertTrue(result is Result.Failure)
        val exception = result.exception
        assertEquals("Network connection lost", exception.message)
    }
}