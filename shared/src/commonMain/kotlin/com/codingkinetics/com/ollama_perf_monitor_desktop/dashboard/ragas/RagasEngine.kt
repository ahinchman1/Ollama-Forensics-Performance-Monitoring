package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

class RagasEngine(
    val forensicsEvaluator: ForensicsEvaluator,
    val loadContexts: suspend () -> Result<List<String>>,
) {

    val onlineValidationContext = """
        OFFICIAL SYSTEM HARDWARE LOGS & BASELINE EXPECTATIONS:
        - This machine has exactly 6 active physical processor cores.
        - True hardware metrics show active process CPU strain stays between 35% and 55% during llama3.2 execution.
        - Safe operating package temperatures for this architecture must register between 60°C and 78°C.
        - Any claim that the system ran completely cold (0% CPU or 0°C), or that it saturated more than 12 cores, is factually incorrect and represents a metrics failure.
    """.trimIndent()

    suspend fun calculateHallucinationScore(
        prompt: String,
        generatedEssay: String,
        peakMetrics: OSMetrics,
    ): Result<EvaluationResult> {

        val hardwareTelemetryText = buildTelemetryBlock(peakMetrics)

        val contexts = when (val contextResult = loadContexts()) {
            is Result.Success -> contextResult.data
            is Result.Failure -> {
                println("Ragas forensics evaluation failed: ${contextResult.exception.message}")
                return Result.Failure(contextResult.exception)
            }
        }

        val truncatedPrompt = prompt.take(400)
        val truncatedResponse = generatedEssay
        val truncatedTelemetry = hardwareTelemetryText.take(200)
        val truncatedBaseline = onlineValidationContext.take(200)
        val truncatedContexts = contexts.joinToString("\n").take(300)

        println("Audit text length: ${truncatedResponse.length} chars")

        val combinedValidationContext = buildString {
            appendLine("--- ACTUAL TELEMETRY ---")
            appendLine(truncatedTelemetry)
            appendLine()
            appendLine("--- BASELINE ---")
            appendLine(truncatedBaseline)
            appendLine()
            appendLine("--- RESPONSE TO AUDIT ---")
            appendLine(truncatedResponse)
            if (truncatedContexts.isNotBlank()) {
                appendLine()
                appendLine("--- SHORT CONTEXT ---")
                appendLine(truncatedContexts)
            }
        }

        println("Analyzing cross-domain statement alignment between hardware truth and generated text...")

        return forensicsEvaluator.evaluateFaithfulness(
            prompt = truncatedPrompt,
            context = combinedValidationContext,
            response = truncatedResponse,
        ).also { result ->
            if (result is Result.Success) {
                val metrics = result.data
                println("\n====================================================================")
                println(" OLLAMA INFERENCE PERFORMANCE FORENSICS (RAGAS VERIFIED) ")
                println("====================================================================")
                println(" Faithfulness Score   : ${String.format("%.4f", metrics.faithfulnessScore)}")
                println(" Hallucination Index  : ${String.format("%.4f", metrics.hallucinationIndex)}")
                println("====================================================================")
            } else if (result is Result.Failure) {
                println("Failed to evaluate payload: ${result.exception.message}")
            }
        }
    }

    private fun buildTelemetryBlock(peakMetrics: OSMetrics): String = buildString {
        appendLine("Actual Physical Telemetry Measured For This Run:")
        appendLine("- Peak CPU Saturation (ps / absolute): ${peakMetrics.processCpuConsumption}%")
        appendLine("- Peak CPU Load (btop / normalized)   : ${peakMetrics.btopProcessCpuConsumption}%")
        appendLine("- Peak Temperature: ${peakMetrics.temperature}°C")
        appendLine("- Live System Threads: ${peakMetrics.threadCount}")
    }
}
