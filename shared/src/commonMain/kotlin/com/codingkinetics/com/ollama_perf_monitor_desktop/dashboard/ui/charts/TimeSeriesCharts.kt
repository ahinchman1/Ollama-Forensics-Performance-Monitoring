package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
        isPercentAxis = true,
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
                        horizontalAlignment = Alignment.CenterHorizontally,
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
    yAxisRange: ClosedFloatingPointRange<Double>? = null,
    isPercentAxis: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()

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
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), // Expanded height slightly for comfortable timeline positioning
                contentAlignment = Alignment.Center
            ) {
                if (dataPoints.size < 2) {
                    Text(
                        text = "Awaiting pipeline telemetry execution...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val timestamps = dataPoints.map { it.first }
                    val values = dataPoints.map { it.second }
                    val minTime = timestamps.minOrNull() ?: 0L
                    val maxTime = timestamps.maxOrNull() ?: 1L

                    val peakValue = values.maxOrNull() ?: 0.0

                    val minValue = if (isPercentAxis) 0.0 else (yAxisRange?.start ?: values.minOrNull() ?: 0.0)
                    val maxValue = if (isPercentAxis) {
                        100.0
                    } else {
                        (yAxisRange?.endInclusive ?: values.maxOfOrNull { it } ?: 1.0)
                    }

                    val valueRange = if (maxValue > minValue) maxValue - minValue else 1.0
                    val timeRange = if (maxTime > minTime) (maxTime - minTime).toFloat() else 1f

                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val labelStyle = MaterialTheme.typography.labelSmall

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val leftPadding = 56.dp.toPx()
                        val rightPadding = 16.dp.toPx()
                        val topPadding = 16.dp.toPx()
                        val bottomPadding = 32.dp.toPx() // Room for the timeline labels below the X axis

                        val chartLeft = leftPadding
                        val chartRight = size.width - rightPadding
                        val chartBottom = size.height - bottomPadding
                        val chartWidth = chartRight - chartLeft
                        val chartHeight = chartBottom - topPadding

                        // X coordinate calculation
                        fun xFor(time: Long): Float {
                            return chartLeft + ((time - minTime).toFloat() / timeRange) * chartWidth
                        }

                        fun yFor(value: Double): Float {
                            val clampedValue = value.coerceIn(minValue, maxValue)
                            val fraction = (clampedValue - minValue) / valueRange
                            return chartBottom - (fraction.toFloat() * chartHeight)
                        }

                        val axisColor = Color.Gray.copy(alpha = 0.5f)
                        val gridColor = Color.Gray.copy(alpha = 0.2f)

                        val yAxisTicks = if (isPercentAxis) {
                            listOf(maxValue to "100%", minValue + valueRange / 2.0 to "50%", minValue to "0%")
                        } else {
                            listOf(
                                maxValue to valueFormatter(maxValue),
                                minValue + valueRange / 2.0 to valueFormatter(minValue + valueRange / 2.0),
                                minValue to valueFormatter(minValue)
                            )
                        }

                        yAxisTicks.forEach { (tickValue, tickLabel) ->
                            val y = yFor(tickValue)

                            // Render horizontal grid helper lines
                            drawLine(
                                color = gridColor,
                                start = Offset(chartLeft, y),
                                end = Offset(chartRight, y),
                                strokeWidth = 1.dp.toPx(),
                            )

                            val measuredLabel = textMeasurer.measure(
                                text = tickLabel,
                                style = labelStyle,
                            )

                            drawText(
                                textLayoutResult = measuredLabel,
                                color = labelColor,
                                topLeft = Offset(
                                    x = chartLeft - measuredLabel.size.width - 8.dp.toPx(),
                                    y = y - measuredLabel.size.height / 2f,
                                ),
                            )
                        }

                        // peak value
                        if (values.isNotEmpty()) {
                            val peakY = yFor(peakValue)

                            val peakLabelText = "PEAK: ${valueFormatter(peakValue)}"
                            val peakLabel = textMeasurer.measure(
                                text = peakLabelText,
                                style = labelStyle.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )

                            drawText(
                                textLayoutResult = peakLabel,
                                color = lineColor.copy(alpha = 0.85f),
                                topLeft = Offset(
                                    x = chartRight - peakLabel.size.width - 4.dp.toPx(),
                                    y = (peakY - peakLabel.size.height - 2.dp.toPx()).coerceAtLeast(topPadding)
                                )
                            )

                            drawLine(
                                color = lineColor.copy(alpha = 0.4f),
                                start = Offset(chartLeft, peakY),
                                end = Offset(chartRight, peakY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(10f, 10f),
                                    phase = 0f
                                )
                            )
                        }

                        // Render main vertical Y Axis line
                        drawLine(
                            color = axisColor,
                            start = Offset(chartLeft, topPadding),
                            end = Offset(chartLeft, chartBottom),
                            strokeWidth = 1.5.dp.toPx(),
                        )

                        // Render main horizontal X Axis line
                        drawLine(
                            color = axisColor,
                            start = Offset(chartLeft, chartBottom),
                            end = Offset(chartRight, chartBottom),
                            strokeWidth = 1.5.dp.toPx(),
                        )

                        // FIXED: Timeline labels ("0s" and "96.8s") are drawn inside the Canvas, precisely aligned with the X-Axis endpoints
                        val zeroLabel = textMeasurer.measure(text = "0s", style = labelStyle)
                        drawText(
                            textLayoutResult = zeroLabel,
                            color = labelColor,
                            topLeft = Offset(
                                x = chartLeft,
                                y = chartBottom + 6.dp.toPx()
                            )
                        )

                        val endLabelText = "%.1fs".format((maxTime - minTime) / 1000.0)
                        val endLabel = textMeasurer.measure(text = endLabelText, style = labelStyle)
                        drawText(
                            textLayoutResult = endLabel,
                            color = labelColor,
                            topLeft = Offset(
                                x = chartRight - endLabel.size.width,
                                y = chartBottom + 6.dp.toPx()
                            )
                        )

                        // Continuous path tracing line
                        val path = Path().apply {
                            dataPoints.forEachIndexed { index, (time, value) ->
                                val x = xFor(time)
                                val y = yFor(value)
                                if (index == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.dp.toPx()),
                        )

                        // Plot individual trace nodes
                        dataPoints.forEach { (time, value) ->
                            drawCircle(
                                color = lineColor,
                                radius = 2.5.dp.toPx(),
                                center = Offset(xFor(time), yFor(value)),
                            )
                        }
                    }
                }
            }

            if (dataPoints.size >= 2) {
                val values = dataPoints.map { it.second }
                val minValue = yAxisRange?.start ?: values.minOrNull() ?: 0.0
                val maxValue = yAxisRange?.endInclusive ?: values.maxOrNull() ?: 1.0

                // 1. calculate the local delta (average absolute jump from point to point)
                val successiveJumps = values.zipWithNext { a, b -> kotlin.math.abs(a - b) }
                val localDelta = if (successiveJumps.isNotEmpty()) successiveJumps.average() else 0.0

                // 2. classify based on how chaotic the local data points are
                val (statusColor, statusText) = when {
                    localDelta > 20.0 -> MaterialTheme.colorScheme.error to "Severe Thrashing"
                    localDelta > 8.0  -> Color(0xFFFFB300) to "High Volatility"
                    else -> Color(0xFF4CAF50) to "Stable Execution"
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bound Limits: ${valueFormatter(minValue)} - ${valueFormatter(maxValue)}" +
                            if (valueLabel.isNotEmpty()) " $valueLabel" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Δ: %.1f%% (%s)".format(localDelta, statusText),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = statusColor
                )
            }
        }
    }
}
