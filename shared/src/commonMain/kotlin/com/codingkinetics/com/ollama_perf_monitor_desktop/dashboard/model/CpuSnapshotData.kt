package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

/**
 * Parsed CPU snapshot used internally while interpreting a btop pane.
 *
 * @param globalTemperature global CPU package temperature in degrees Celsius (°C); 0 if btop did
 *   not report a global value.
 * @param processCpu per-process CPU utilization of the Ollama server as a percentage (0-100+),
 *   from `ps`.
 * @param cores per-core telemetry at the time of capture.
 */
data class CpuSnapshotData(
    val globalTemperature: Int,
    val processCpu: Long,
    val cores: List<Core>,
)
