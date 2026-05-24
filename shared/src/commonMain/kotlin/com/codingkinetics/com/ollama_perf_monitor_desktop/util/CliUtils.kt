package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import java.io.File

const val tmuxSessionName = "model_profiling_session"

val tmuxExecutable = resolveExecutable("tmux")
val ollamaExecutable = resolveExecutable("ollama")
val btopExecutable = resolveExecutable("btop")

fun ProcessBuilder.withCliPath(): ProcessBuilder {
    val env = this.environment()
    val systemPath = System.getenv("PATH") ?: "/usr/bin:/bin"
    val homebrewPaths = "/opt/homebrew/bin:/usr/local/bin"
    env["PATH"] = "$homebrewPaths:$systemPath"
    return this
}

fun resolveExecutable(command: String): String {
    val targetPaths = listOf(
        "/opt/homebrew/bin/$command",
        "/usr/local/bin/$command",
        "/usr/bin/$command",
        "/bin/$command",
        "/Users/amandahinchman-dominguez/Documents/repos/ollama/$command",
    )
    val found = targetPaths.firstOrNull { File(it).exists() }
    if (found != null) return found

    return try {
        val process = ProcessBuilder("bash", "-lc", "which $command").start()
        val path = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor() == 0 && path.isNotBlank()) path else command
    } catch (_: Exception) {
        command
    }
}