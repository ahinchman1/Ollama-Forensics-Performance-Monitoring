package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

/**
 * Snapshot of operating-system telemetry captured while an Ollama job runs.
 *
 * Values represent the **peak** reading observed during a run (not an average or instantaneous
 * sample) unless documented otherwise. Most fields are populated from a `btop` pane parsed via
 * [com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics.MetricsCollector];
 * [processCpuConsumption] additionally comes from `ps`.
 *
 * If telemetry cannot be captured (e.g. `btop`/`tmux` unavailable or the pane is unreadable), the
 * numeric fields fall back to their zero defaults (0 / 0.0) and [cores]/[cpuGraph] to empty — the
 * run still completes, but CPU/temperature metrics will read as zero rather than failing.
 */
data class OSMetrics(
    /** CPU package temperature in degrees Celsius (°C). Peak observed; 0 if never collected. */
    val temperature: Int,
    /**
     * Per-process CPU utilization of the Ollama server as a percentage (0-100+, can exceed 100 on
     * multi-threaded workloads), sourced from `ps`. Peak observed.
     */
    val processCpuConsumption: Long,
    /** Raw captured CPU telemetry text (braille graph) from the btop pane. */
    val cpuTelemetry: String,
    /** Per-core snapshots; empty if none were parsed. */
    val cores: List<Core> = listOf(),
    /** Captured braille CPU graph lines from btop. */
    val cpuGraph: List<String> = listOf(),
    /** Live thread count of the Ollama process. Peak observed. */
    val threadCount: Int = 0,
    /**
     * Per-process CPU utilization (%) of the Ollama server as reported by btop (normalized,
     * 0-100+). Peak observed.
     */
    val btopProcessCpuConsumption: Double = 0.0,
    /**
     * Sum of all core CPU percentages (aggregate load); can exceed 100% on multi-core machines.
     * Peak observed.
     */
    val aggregateCpuConsumption: Double = 0.0,
)

/**
 * Per-core CPU telemetry.
 *
 * @param name core identifier as reported by btop (e.g. `C0`).
 * @param temperature core package temperature in degrees Celsius (°C).
 * @param cpuPercentage per-core CPU utilization as a percentage (0-100).
 */
data class Core(
    val name: String,
    val temperature: Int,
    val cpuPercentage: Double = 0.0,
)