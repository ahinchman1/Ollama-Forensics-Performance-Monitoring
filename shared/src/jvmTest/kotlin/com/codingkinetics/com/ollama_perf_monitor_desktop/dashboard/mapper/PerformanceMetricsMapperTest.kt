package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaJobResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceMetricsMapperTest {

    @Test
    fun `mapOllamaResponseToDomain should successfully map all fields when inputs are valid`() {
        // Given
        val prompt = "Translate hello to french"

        val completedData = OllamaResponseCompletedData(
            model = "llama3.2",
            createdAt = "2024-01-01T00:00:00Z",
            response = "",
            done = true,
            doneReason = "stop",
            totalDuration = 5000L,
            loadDuration = 1000L,
            promptEvalCount = 5,
            promptEvalDuration = 200L,
            generatedTokenCount = 2,
            generationDuration = 800L
        )

        val ollamaJobResult = OllamaJobResult(
            generatedText = "Bonjour",
            completedData = completedData
        )

        val btopSnapshot = OSMetrics(
            temperature = 45,
            processCpuConsumption = 12,
            cpuTelemetry = "baseline"
        )

        val ragasEvaluation = EvaluationResult(
            hallucinationIndex = 0.05,
            faithfulnessScore = 0.95
        )

        // When
        val result = mapOllamaResponseToDomain(
            prompt = prompt,
            ollamaJobResult = ollamaJobResult,
            btopSnapshot = btopSnapshot,
            ragasEvaluation = ragasEvaluation
        )

        // Then
        assertTrue(result is Result.Success)
        val metrics = result.data

        assertEquals(prompt, metrics.prompt)
        assertEquals("Bonjour", metrics.output)
        assertEquals(btopSnapshot, metrics.osMetrics)
        assertEquals(1000L, metrics.loadDurationNanos)
        assertEquals(5000L, metrics.totalDurationNanos)
        assertTrue(metrics.done)
        assertEquals("stop", metrics.doneReason)
        assertEquals(5, metrics.promptTokensCount)
        assertEquals(200L, metrics.promptEvaluationDurationNanos)
        assertEquals(2, metrics.generatedTokensCount)
        assertEquals(800L, metrics.generationDurationNanos)
        assertEquals(0.05, metrics.hallucinationIndex)
        assertEquals(0.95, metrics.faithfulnessScore)
    }
}