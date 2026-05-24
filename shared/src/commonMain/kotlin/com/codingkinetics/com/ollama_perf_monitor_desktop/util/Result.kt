package com.codingkinetics.com.ollama_perf_monitor_desktop.util

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Failure(val exception: Exception) : Result<Nothing>
}