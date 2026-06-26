package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.ragasExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.resolveExecutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

data class EvaluationResult(
    val faithfulnessScore: Double,
    val hallucinationIndex: Double,
)

class RagasEngine(
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

        return evaluatePayload(prompt, generatedEssay, contexts).also { result ->
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

    fun evaluatePayload(prompt: String, generatedEssay: String, contexts: List<String>): Result<EvaluationResult> {
        val pythonInterpreter = resolveExecutable("../.venv/bin/python3")
        val combinedContexts = contexts.joinToString("\n")
        return try {
            val process = ProcessBuilder(
                pythonInterpreter,
                ragasExecutable,
                prompt,
                combinedContexts,
                generatedEssay
            ).redirectErrorStream(true)
                .start()

            println("Executing Ragas forensics script...")

            val output = process.inputStream.bufferedReader().use {
                val text = it.readText()
                println(text)
                text
            }.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.contains("RESULT_METRICS:")) {
                val hallucination = output.substringAfter("RESULT_SCORE:")
                    .trim()
                    .toDoubleOrNull() ?: 0.0

                val faithfulness = output.substringAfter("└── Faithfulness Score : ")
                    .substringBefore("\n")
                    .trim()
                    .toDoubleOrNull() ?: 0.0

                return Result.Success(
                    EvaluationResult(
                        faithfulnessScore = faithfulness,
                        hallucinationIndex = hallucination
                    )
                )
            } else {
                println("Metrics script crashed with exit code $exitCode. Output:\n$output")
                Result.Failure(Exception("Metrics script crashed with exit code $exitCode. Output:\n$output"))
            }
        } catch (e: Exception) {
            println("Failed to execute Ragas forensics script: ${e.message}")
            Result.Failure(Exception("Failed to execute Ragas forensics script: ${e.message}"))
        }
    }
}