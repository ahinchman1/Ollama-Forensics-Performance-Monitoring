package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas.EvaluationResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

internal fun mapOllamaResponseToDomain(
    prompt: String,
    responsePayload: OllamaResponseCompletedData,
    btopSnapshot: OSMetrics,
    ragasEvaluation: EvaluationResult,
): Result<PerformanceMetrics> = try {
        val metrics = PerformanceMetrics(
            prompt = prompt,
            output = responsePayload.response,
            osMetrics = btopSnapshot,
            loadDurationNanos = responsePayload.loadDuration,
            totalDurationNanos = responsePayload.totalDuration,
            done = responsePayload.done,
            doneReason = responsePayload.doneReason,
            promptTokensCount = responsePayload.promptEvalCount,
            promptEvaluationDurationNanos = responsePayload.promptEvalDuration,
            generatedTokensCount = responsePayload.tabCount,
            generationDurationNanos = responsePayload.tabDuration,
            hallucinationIndex = ragasEvaluation.hallucinationIndex,
            faithfulnessScore = ragasEvaluation.faithfulnessScore,
        )
        Result.Success(metrics)
    } catch (e: Exception) {
        println("Unable to map data to performance Metrics. Cause: ${e.message}")
        Result.Failure(e)
    }
