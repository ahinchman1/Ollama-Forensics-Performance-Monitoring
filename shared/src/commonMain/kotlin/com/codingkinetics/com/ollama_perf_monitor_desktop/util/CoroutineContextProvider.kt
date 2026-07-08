package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over the coroutine dispatchers used across the app.
 *
 * Centralizing dispatcher selection here keeps platform/SDK execution policy in one place and
 * makes it overridable for tests. Code that performs IO or process orchestration should receive
 * these dispatchers instead of referencing [kotlinx.coroutines.Dispatchers]
 * directly, so behavior is consistent and testable.
 */
interface CoroutineContextProvider {
    /** Dispatcher for UI / main-thread work (Compose). */
    val mainDispatcher: CoroutineDispatcher
    /** Immediate variant of [mainDispatcher]; use when already on the main thread. */
    val mainImmediateDispatcher: CoroutineDispatcher
    /** Dispatcher for CPU-bound, non-IO background work. */
    val defaultDispatcher: CoroutineDispatcher
    /** Dispatcher for blocking IO and external process/HTTP calls. */
    val ioDispatcher: CoroutineDispatcher
}

class CoroutineContextProviderImpl : CoroutineContextProvider {
    override val mainDispatcher: CoroutineDispatcher by lazy { Dispatchers.Main }
    override val mainImmediateDispatcher: CoroutineDispatcher by lazy { Dispatchers.Main.immediate }
    override val defaultDispatcher: CoroutineDispatcher by lazy { Dispatchers.Default }
    override val ioDispatcher: CoroutineDispatcher by lazy { Dispatchers.IO }
}