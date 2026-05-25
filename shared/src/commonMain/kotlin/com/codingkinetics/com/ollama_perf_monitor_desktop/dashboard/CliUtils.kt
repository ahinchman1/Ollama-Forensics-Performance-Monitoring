package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard

import java.io.File
import java.util.concurrent.TimeUnit

const val tmuxSessionName = "model_profiling_session"

val tmuxExecutable = resolveExecutable("tmux")
val ollamaExecutable = resolveExecutable("ollama")
val btopExecutable = resolveExecutable("btop")

internal fun ProcessBuilder.withCliPath(): ProcessBuilder {
    val env = this.environment()
    val systemPath = System.getenv("PATH") ?: "/usr/bin:/bin"
    val homebrewPaths = "/opt/homebrew/bin:/usr/local/bin"
    env["PATH"] = "$homebrewPaths:$systemPath"
    return this
}

internal fun resolveExecutable(command: String): String {
    val currentProjectDir = File(System.getProperty("user.dir"))
    val repositoriesParentDir = currentProjectDir.parentFile
    val localOllamaRepoBinary = if (repositoriesParentDir != null) {
        File(repositoriesParentDir, "ollama/$command").absolutePath
    } else {
        null
    }
    val targetPaths = mutableListOf(
        "/opt/homebrew/bin/$command",
        "/usr/local/bin/$command",
        "/usr/bin/$command",
        "/bin/$command",
    )

    localOllamaRepoBinary?.let { path ->
        targetPaths.add(0, path)
    }

    val found = targetPaths.firstOrNull { File(it).exists() }
    if (found != null) return found

    return try {
        val process = ProcessBuilder("bash", "-lc", "which $command").start()
        val path = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor() == 0 && path.isNotBlank()) path else command
    } catch (e: Exception) {
        println("Unable to run ollama. Cause of error: ${e.message}")
        command
    }
}

internal fun runCommandIgnoringErrors(vararg command: String) {
    runCatching {
        ProcessBuilder(*command).withCliPath().start().waitFor(500, TimeUnit.MILLISECONDS)
    }.onFailure { e ->
        println("Ignoring error running command: ${command.joinToString(" ")}. Cause: ${e.message}")
    }
}

internal fun commandExists(command: String): Boolean {
    val executable = resolveExecutable(command)
    if (File(executable).isAbsolute && File(executable).exists()) {
        return true
    }

    return try {
        val process = ProcessBuilder("bash", "-c", "command -v $command")
            .withCliPath()
            .start()
        process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}