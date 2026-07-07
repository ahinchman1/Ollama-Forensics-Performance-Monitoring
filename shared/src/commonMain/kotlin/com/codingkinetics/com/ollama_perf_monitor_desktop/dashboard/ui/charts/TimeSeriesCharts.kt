package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking.BenchmarkScenarioResult
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.TokenTimeSeriesSnapshot

@Composable
fun CpuTimeSeriesChart(
    snapshots: List<CpuTimeSeriesSnapshot>,
    modifier: Modifier = Modifier,
) {
    TimeSeriesLineChart(
        title = "CPU Consumption Over Time",
        dataPoints = snapshots.map { it.timestampMillis to it.cpuConsumption },
        valueFormatter = { "%.1f%%".format(it) },
        lineColor = Color(0xFFEF5350),
        modifier = modifier,
    )
}

@Composable
fun TokenExpenditureChart(
    snapshots: List<TokenTimeSeriesSnapshot>,
    modifier: Modifier = Modifier,
) {
    val dataPoints = snapshots.map { it.timestampMillis to it.cumulativeGeneratedTokens.toDouble() }
    TimeSeriesLineChart(
        title = "Token Expenditure Over Time",
        dataPoints = dataPoints,
        valueFormatter = { "%.0f".format(it) },
        lineColor = Color(0xFF66BB6A),
        valueLabel = "Tokens",
        modifier = modifier,
    )
}

@Composable
fun TokenComparisonBarChart(
    results: List<BenchmarkScenarioResult>,
    modifier: Modifier = Modifier,
) {
    val maxTokens = results.maxOfOrNull { it.generatedTokens.toDouble() } ?: 1.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Token Expenditure by Scenario",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                results.forEach { result ->
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height((result.generatedTokens.toDouble() / maxTokens * 80).dp)
                                .background(
                                    when {
                                        result.hallucinationIndex > 0.5 -> MaterialTheme.colorScheme.error
                                        result.hallucinationIndex > 0.25 -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${result.generatedTokens}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Scenario ${result.scenarioId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSeriesLineChart(
    title: String,
    dataPoints: List<Pair<Long, Double>>,
    valueFormatter: (Double) -> String,
    lineColor: Color,
    modifier: Modifier = Modifier,
    valueLabel: String = "",
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (dataPoints.size < 2) {
                Text(
                    text = "Collecting data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val timestamps = dataPoints.map { it.first }
                val values = dataPoints.map { it.second }
                val minTime = timestamps.minOrNull() ?: 0L
                val maxTime = timestamps.maxOrNull() ?: 1L
                val minValue = values.minOrNull() ?: 0.0
                val maxValue = values.maxOrNull() ?: 1.0
                val valueRange = if (maxValue > minValue) maxValue - minValue else 1.0
                val timeRange = if (maxTime > minTime) (maxTime - minTime).toFloat() else 1f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "0s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "%.1fs".format((maxTime - minTime) / 1000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val padding = 20.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2

                    dataPoints.forEach { (time, value) ->
                        val x = padding + ((time - minTime).toFloat() / timeRange) * chartWidth
                        val y = padding + chartHeight - ((value - minValue) / valueRange.toFloat()) * chartHeight
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y.toFloat()),
                        )
                    }

                    if (dataPoints.size >= 2) {
                        val path = Path().apply {
                            dataPoints.forEachIndexed { index, (time, value) ->
                                val x = padding + ((time - minTime).toFloat() / timeRange) * chartWidth
                                val y = padding + chartHeight - ((value - minValue) / valueRange.toFloat()) * chartHeight
                                if (index == 0) moveTo(x, y.toFloat()) else lineTo(x, y.toFloat())
                            }
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                if (valueLabel.isNotEmpty()) {
                    Text(
                        text = "Max: ${valueFormatter(maxValue)} $valueLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
