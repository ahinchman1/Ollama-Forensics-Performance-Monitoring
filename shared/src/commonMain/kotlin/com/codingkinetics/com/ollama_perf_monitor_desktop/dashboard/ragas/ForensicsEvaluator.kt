package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class ForensicsEvaluator(private val client: HttpClient) {
    private val apiKey = System.getenv("GROQ_API_KEY") ?: ""
    private val json = Json { ignoreUnknownKeys = true }

    class GroqRateLimitException(
        message: String,
        val retryAfterSeconds: String = "60"
    ) : IllegalStateException(message)

    suspend fun evaluateFaithfulness(
        prompt: String,
        context: String,
        response: String,
    ): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        if (response.isBlank()) {
            return@withContext Result.Failure(Exception("No response to evaluate."))
        }

        val systemPrompt = """
            You are a strict forensic auditor. Output JSON only.
            Given a user prompt, a model response, and telemetry context, score faithfulness 0.00-1.00.
            0.00 = response ignores the prompt or contradicts context.
            1.00 = response directly answers the prompt using only context values.
            Return: {"faithfulnessScore": X.XX}
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("[USER PROMPT]")
            appendLine(prompt)
            appendLine()
            appendLine("[MODEL RESPONSE TO AUDIT]")
            appendLine(response)
            appendLine()
            appendLine("[CONTEXT - TELEMETRY BASELINE]")
            appendLine(context)
            appendLine()
            appendLine("TASK: Does the response directly and accurately answer the user prompt using ONLY the context values above? Output ONLY JSON: {\"faithfulnessScore\": X.XX}")
        }

        try {
            val firstRaw = callGroq(systemPrompt, userPrompt)
            println("[DEBUG] First raw response: ${firstRaw.take(500)}")
            val firstScore = extractScoreFromJson(firstRaw)
            val firstValid = isScoreValid(firstRaw, firstScore)
            if (firstValid) {
                return@withContext Result.Success(EvaluationResult(firstScore, 1.0 - firstScore))
            }

            val repairPrompt = "Your last reply was invalid. Output ONLY JSON: {\"faithfulnessScore\": 0.85}"
            val secondRaw = callGroq(systemPrompt, repairPrompt)
            println("[DEBUG] Second raw response: ${secondRaw.take(500)}")
            val secondScore = extractScoreFromJson(secondRaw)

            Result.Success(EvaluationResult(secondScore, 1.0 - secondScore))
        } catch (e: Exception) {
            println("[ERROR] Forensics evaluation failed: ${e.message}")
            Result.Failure(e)
        }
    }

    private suspend fun callGroq(systemPrompt: String, userPrompt: String): String {
        val httpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                GroqRequest(
                    model = "llama-3.3-70b-versatile",
                    messages = listOf(
                        GroqMessage("system", systemPrompt),
                        GroqMessage("user", userPrompt)
                    ),
                    temperature = 0.0
                )
            )
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            val message = runCatching { httpResponse.body<GroqError>() }.getOrNull()?.error?.message
                ?: errorBody.take(200)
            
            if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = httpResponse.headers["Retry-After"] ?: "60"
                throw GroqRateLimitException("Groq rate limited. Retry after $retryAfter seconds.", retryAfter)
            }
            
            throw Exception("Groq API error ${httpResponse.status}: $message")
        }

        return httpResponse.bodyAsText()
    }

    private fun extractScoreFromJson(rawJson: String): Double {
        val content = extractGroqContent(rawJson)
        println("[DEBUG] Extracted content for parsing: ${content?.take(200) ?: rawJson.take(200)}")

        val direct = """"faithfulnessScore"\s*[:=,]\s*([0-9]*\.?[0-9]+)""".toRegex().find(content ?: rawJson)
        if (direct != null) {
            val score = direct.groupValues[1].toDoubleOrNull()?.coerceIn(0.0, 1.0)
            println("[DEBUG] Direct faithfulnessScore regex matched: '${direct.groupValues[1]}' → $score")
            return score ?: FALLBACK_SCORE
        }

        val alt = """"?(score|faithfulness)"?\s*[:=,]\s*([0-9]*\.?[0-9]+)""".toRegex().find(content ?: rawJson)
        if (alt != null) {
            val score = alt.groupValues[2].toDoubleOrNull()?.coerceIn(0.0, 1.0)
            println("[DEBUG] Alt key '${alt.groupValues[1]}' matched: '${alt.groupValues[2]}' → $score")
            return score ?: FALLBACK_SCORE
        }

        val decimal = Regex("""([0-9]*\.[0-9]+)""").find(content ?: rawJson)
        val decimalScore = decimal?.groupValues?.get(1)?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
        println("[DEBUG] No key match, decimal fallback: $decimalScore (from content: ${content != null})")
        return decimalScore ?: FALLBACK_SCORE
    }

    private fun extractGroqContent(rawJson: String): String? {
        return try {
            val response = json.decodeFromString(GroqResponse.serializer(), rawJson)
            response.choices.firstOrNull()?.message?.content?.also {
                println("[DEBUG] Parsed Groq content via JSON: ${it.take(100)}")
            }
        } catch (e: Exception) {
            println("[DEBUG] Failed to parse Groq JSON: ${e.message}")
            println("[DEBUG] Raw response: ${rawJson.take(300)}")
            null
        }
    }

    private fun isScoreValid(rawJson: String, score: Double): Boolean {
        val content = extractGroqContent(rawJson)
        val hasFaithfulnessKey = content?.contains("faithfulnessScore") == true
        val usedDecimalFallback = !hasFaithfulnessKey && Regex("""([0-9]*\.[0-9]+)""").find(content ?: rawJson) != null
        println("[DEBUG] isScoreValid: score=$score, hasFaithfulnessKey=$hasFaithfulnessKey, usedDecimalFallback=$usedDecimalFallback")
        return hasFaithfulnessKey
    }

    companion object {
        private const val FALLBACK_SCORE = 0.5
    }
}
