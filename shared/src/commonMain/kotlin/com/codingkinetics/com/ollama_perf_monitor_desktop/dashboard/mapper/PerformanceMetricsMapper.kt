package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.mapper

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.BtopMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

internal fun mapOllamaResponseToDomain(
    responsePayload: OllamaResponseCompletedData,
    btopSnapshot: BtopMetrics,
): Result<PerformanceMetrics> = try {
        val metrics = PerformanceMetrics(
            osMetrics = btopSnapshot,
            loadDurationNanos = responsePayload.loadDuration,
            totalDurationNanos = responsePayload.totalDuration,
            done = responsePayload.done,
            doneReason = responsePayload.doneReason,
            promptTokensCount = responsePayload.promptEvalCount,
            promptEvaluationDurationNanos = responsePayload.promptEvalDuration,
            generatedTokensCount = responsePayload.tabCount,
            generationDurationNanos = responsePayload.tabDuration,
        )
        Result.Success(metrics)
    } catch (e: Exception) {
        println("Unable to map data to performance Metrics. Cause: ${e.message}")
        Result.Failure(e)
    }
