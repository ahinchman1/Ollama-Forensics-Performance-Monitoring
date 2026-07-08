package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface CoroutineContextProvider {
    val mainDispatcher: CoroutineDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher
    val defaultDispatcher: CoroutineDispatcher
    val ioDispatcher: CoroutineDispatcher
}

class CoroutineContextProviderImpl : CoroutineContextProvider {
    override val mainDispatcher: CoroutineDispatcher by lazy { Dispatchers.Main }
    override val mainImmediateDispatcher: CoroutineDispatcher by lazy { Dispatchers.Main.immediate }
    override val defaultDispatcher: CoroutineDispatcher by lazy { Dispatchers.Default }
    override val ioDispatcher: CoroutineDispatcher by lazy { Dispatchers.IO }
}