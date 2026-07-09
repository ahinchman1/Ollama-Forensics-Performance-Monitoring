package com.codingkinetics.com.ollama_perf_monitor_desktop.util

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Failure(val exception: Exception) : Result<Nothing>
}

inline fun <T> runCatchingDomain(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Throwable) {
        Result.Failure(e as Exception)
    }
}

inline fun <T> Result<T>.getOrElse(onFailure: (exception: Throwable) -> T): T {
    return when (this) {
        is Result.Success -> this.data
        is Result.Failure -> onFailure(this.exception)
    }
}

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return when (this) {
        is Result.Success -> transform(this.data)
        is Result.Failure -> Result.Failure(this.exception)
    }
}