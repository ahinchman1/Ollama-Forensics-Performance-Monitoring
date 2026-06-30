package com.codingkinetics.com.ollama_perf_monitor_desktop.util

internal fun Long.nanosToSeconds(): Double = try {
    this / 1_000_000_000.0
} catch (e: Exception) {
    println("Unable to capture nanos. Cause: $e")
    0.0
}

internal fun List<Int>.roverage(): Double = if (isEmpty()) 0.0 else this.sum().toDouble() / this.size

/**
 * Extension function to verify if a character belongs to the Unicode Braille Patterns block.
 * Unicode range: U+2800 – U+28FF
 */
fun Char.isBraille(): Boolean {
    return this.code in 0x2800..0x28FF
}