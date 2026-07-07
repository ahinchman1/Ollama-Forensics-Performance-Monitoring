package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.formatter

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics

class PerformanceMetricsFormatter {
    fun formatDiagnostics(metrics: PerformanceMetrics, model: String): String {
        return """
            ================================================================================
              OLLAMA WORKLOAD DIAGNOSTICS // MODEL: $model
            ================================================================================
              [ EXECUTIVE VERDICT ]
              STATUS: SUCCESS (${metrics.doneReason})
              TOTAL WALL TIME: ${metrics.formattedTotalDuration}
              
              [ ENGINE THROUGHPUT ]
              PHASE 1: Ingestion (Reading Prompt)
              ├── Tokens Evaluated: ${metrics.promptTokensCount}
              └── Processing Speed: ${metrics.formattedIngestionSpeed}
            
              PHASE 2: Generation (Writing Response)
              ├── Tokens Streamed:  ${metrics.generatedTokensCount}
              └── Generation Speed: ${metrics.formattedGenerationSpeed}
            
               [ HARDWARE FORENSICS SUMMARY ]
               PROCESSOR CPU LOAD: ${metrics.osMetrics.temperature}°F Avg Total Package
               ├── Ollama Active CPU Strain (ps / absolute): ${metrics.osMetrics.processCpuConsumption}%
               └── Ollama CPU Load (btop / normalized)       : ${metrics.osMetrics.btopProcessCpuConsumption}%
             ================================================================================
        """.trimIndent()
    }

    fun formatSystemSnapshot(metrics: PerformanceMetrics): String = """
        ================================================================================
        SYSTEM RESOURCE SNAPSHOT
        ================================================================================
        GLOBAL TEMPERATURE : ${metrics.osMetrics.temperature}°C
        TOTAL SYSTEM THREADS: ${metrics.osMetrics.threadCount}
        ACTIVE CORES DETECTED: ${metrics.osMetrics.cores.size}
        
        [ CORE TELEMETRY DETAILED BREAKDOWN ]
        ${metrics.osMetrics.cores.joinToString("\n") { "├── ${it.name}: ${it.temperature}°C" }}
        ================================================================================
    """.trimIndent()
}