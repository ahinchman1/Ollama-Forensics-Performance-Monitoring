package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BenchmarkReportView(report: BenchmarkSuiteReport) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "📊 Ollama Forensics Performance Report",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item { PerformanceSummaryCard(report) }

        item { BarChartCard(report.results) }

        items(report.results) { result ->
            ScenarioResultCard(result)
        }
    }
}

@Composable
private fun PerformanceSummaryCard(report: BenchmarkSuiteReport) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🏎️ Core Engine Velocity Baseline",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Avg Ingestion Speed: ${String.format("%.1f", report.averagePromptIngestionSpeed)} t/s")
            Text("Avg Generation Speed: ${String.format("%.2f", report.averageTokenGenerationSpeed)} t/s")
            Text("Mean Total Time: ${String.format("%.1f", report.meanTotalExecutionTime)}s")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🌡️ Hardware Strain Profile",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Peak CPU (ps): ${report.peakCpuConsumption}%")
            Text("Peak CPU (btop): ${String.format("%.1f", report.peakBtopCpuConsumption)}%")
            Text("Peak Temp: ${report.peakTemperature}°C")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🧵 Thread Concurrency",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Peak Threads: ${report.peakThreadCount}")
            if (report.threadSpikeDetected) {
                Text(
                    text = "⚠️ Thread Spike Detected",
                    color = Color(0xFFEF5350),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun BarChartCard(results: List<BenchmarkScenarioResult>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(120.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📊 Token Generation Speed (t/s)",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            BarChart(results)
        }
    }
}

@Composable
private fun BarChart(results: List<BenchmarkScenarioResult>) {
    val speeds = results.map { it.tokenGenerationSpeed }
    val maxSpeed = speeds.maxOrNull() ?: 1.0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        results.forEach { result ->
            val barHeight = if (maxSpeed > 0) {
                (result.tokenGenerationSpeed / maxSpeed * 50).dp
            } else 0.dp
            
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(barHeight)
                        .background(
                            when {
                                result.hallucinationIndex > 0.5 -> Color(0xFFEF5350)
                                result.hallucinationIndex > 0.25 -> Color(0xFFFFEE58)
                                else -> Color(0xFF66BB6A)
                            }
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.scenarioId,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ScenarioResultCard(result: BenchmarkScenarioResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = when {
                result.hallucinationIndex >= 0.49 && result.hallucinationIndex <= 0.51 -> 
                    Color(0xFFFFF3E0)
                result.hallucinationIndex > 0.5 -> 
                    Color(0xFFFFEBEE)
                else -> 
                    MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🔍 ${result.scenarioLabel}",
                style = MaterialTheme.typography.titleSmall
            )
            Text("Hallucination: ${String.format("%.4f", result.hallucinationIndex)}")
            Text("Faithfulness: ${String.format("%.4f", result.faithfulnessScore)}")
            Text("Gen Speed: ${result.formattedGenerationSpeed}")
            Text("Tokens: ${result.promptTokens} → ${result.generatedTokens}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🌡️ OS Metrics",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("CPU (ps): ${result.osMetrics.processCpuConsumption}% | CPU (btop): ${String.format("%.1f", result.osMetrics.btopProcessCpuConsumption)}% | Temp: ${result.osMetrics.temperature}°C | Threads: ${result.osMetrics.threadCount}")
            if (result.osMetrics.cores.isNotEmpty()) {
                val coreTemps = result.osMetrics.cores.joinToString(", ") { "${it.name}: ${it.temperature}°C" }
                Text("Cores: $coreTemps")
            }
        }
    }
}