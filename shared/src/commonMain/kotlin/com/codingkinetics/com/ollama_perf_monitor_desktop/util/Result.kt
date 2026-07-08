package com.codingkinetics.com.ollama_perf_monitor_desktop.util

/**
 * Domain result type used across the app to represent success/failure without throwing.
 *
 * Use [Success] to carry a value and [Failure] to carry an [Exception]. Prefer `when` matching on
 * the sealed subtypes (or the [getOrElse]/[flatMap] helpers) over catching exceptions. This keeps
 * external-SDK failures (Ollama, Groq, tmux) explicit and recoverable in the UI layer.
 */
sealed interface Result<out T> {
    /** Successful outcome carrying the value of type [T]. */
    data class Success<out T>(val data: T) : Result<T>
    /** Failed outcome carrying the causing [exception]. */
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