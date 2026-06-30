package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

class RagasEngine(
    val forensicsEvaluator: ForensicsEvaluator,
    val loadContexts: suspend () -> Result<List<String>>,
) {

    suspend fun calculateHallucinationScore(prompt: String, generatedEssay: String): Result<EvaluationResult> {
        val contexts = when (val contextResult = loadContexts()) {
            is Result.Success -> contextResult.data
            is Result.Failure -> {
                println("Ragas forensics evaluation failed: ${contextResult.exception.message}")
                return Result.Failure(contextResult.exception)
            }
        }

        println("Analyzing statement alignment across domains...")

        return forensicsEvaluator.evaluateFaithfulness(
            prompt,
            contexts.joinToString("\n"),
            generatedEssay,
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
}