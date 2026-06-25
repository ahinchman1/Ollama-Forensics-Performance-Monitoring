package com.codingkinetics.com.ollama_perf_monitor_desktop.util

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return when (this) {
        is Result.Success -> transform(this.data)
        is Result.Failure -> Result.Failure(this.exception)
    }
}