package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ForensicsEvaluator(private val client: HttpClient) {
    private val apiKey = System.getenv("GROQ_API_KEY") ?: ""

    suspend fun evaluateFaithfulness(prompt: String, context: String, response: String): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        if (response.isBlank()) {
            return@withContext Result.Failure(Exception("No response to evaluate."))
        }

        val evaluationPrompt = """
            You are a rigorous forensic AI performance validator. Your job is to analyze a model's generated response 
            against a source context block.
            
            [Source Context]
            $context
            
            [User Prompt]
            $prompt
            
            [Generated Response]
            $response
            
            Perform two precise analytical evaluations:
            1. Extract the individual claims made in the Generated Response and cross-reference them with the 
            Source Context. Calculate a precision score between 0.0 and 1.0 representing how much of the response 
            is completely faithful to and supported by the context without introducing outside fabrications.
            
            Return your verdict strictly as a valid raw JSON object matching this schema. Do not output markdown code blocks, do not output prose text.
            {
              "faithfulnessScore": 0.85
            }
        """.trimIndent()

        try {
            val httpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    GroqRequest(
                        messages = listOf(
                            GroqMessage(role = "user", content = evaluationPrompt)
                        )
                    )
                )
            }

            val rawBody = httpResponse.bodyAsText()
            val receivedFaithfulness = extractScoreFromJson(rawBody)

            Result.Success(EvaluationResult(
                faithfulnessScore = receivedFaithfulness,
                hallucinationIndex = 1.0 - receivedFaithfulness
            ))
        } catch (e: Exception) {
            println("Failed to execute Ragas forensics script: ${e.message}")
            Result.Failure(e)
        }
    }

    private fun extractScoreFromJson(rawJson: String): Double {
        // Safe primitive extraction fallback loop if the model skips structural parsing layout
        val regex = """"faithfulnessScore"\s*:\s*([0-9.]+)""".toRegex()
        val match = regex.find(rawJson)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
    }
}