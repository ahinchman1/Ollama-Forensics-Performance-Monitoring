package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ollama_perf_monitor_desktop",
    ) {
        App()
    }
}