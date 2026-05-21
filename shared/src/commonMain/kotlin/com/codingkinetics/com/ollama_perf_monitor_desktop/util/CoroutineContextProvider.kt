package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface CoroutineContextProvider {
    val mainDispatcher: CoroutineContext
    val mainImmediateDispatcher: CoroutineContext
    val defaultDispatcher: CoroutineContext
    val ioDispatcher: CoroutineContext
}

class CoroutineContextProviderImpl : CoroutineContextProvider {
    override val mainDispatcher: CoroutineContext by lazy { Dispatchers.Main }
    override val mainImmediateDispatcher: CoroutineContext by lazy { Dispatchers.Main.immediate }
    override val defaultDispatcher: CoroutineContext by lazy { Dispatchers.Default }
    override val ioDispatcher: CoroutineContext by lazy { Dispatchers.IO }
}