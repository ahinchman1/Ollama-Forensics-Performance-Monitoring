package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaJobResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

internal fun mapOllamaResponseToDomain(
    prompt: String,
    ollamaJobResult: OllamaJobResult,
    btopSnapshot: OSMetrics,
    ragasEvaluation: EvaluationResult,
): Result<PerformanceMetrics> = try {
        val metrics = PerformanceMetrics(
            prompt = prompt,
            output = ollamaJobResult.generatedText,
            osMetrics = btopSnapshot,
            loadDurationNanos = ollamaJobResult.completedData.loadDuration,
            totalDurationNanos = ollamaJobResult.completedData.totalDuration,
            done = ollamaJobResult.completedData.done,
            doneReason = ollamaJobResult.completedData.doneReason,
            promptTokensCount = ollamaJobResult.completedData.promptEvalCount,
            promptEvaluationDurationNanos = ollamaJobResult.completedData.promptEvalDuration,
            generatedTokensCount = ollamaJobResult.completedData.generatedTokenCount,
            generationDurationNanos = ollamaJobResult.completedData.generationDuration,
            hallucinationIndex = ragasEvaluation.hallucinationIndex,
            faithfulnessScore = ragasEvaluation.faithfulnessScore,
        )
        Result.Success(metrics)
    } catch (e: Exception) {
        println("Unable to map data to performance Metrics. Cause: ${e.message}")
        Result.Failure(e)
    }
