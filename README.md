# Ollama Forensics Performance Monitor

A Compose Desktop application (Kotlin Multiplatform targeting the JVM) that forensically
measures the performance of local [Ollama](https://ollama.com) LLM inference.

For a given model and prompt, the app:

- Starts a local Ollama server and streams a generation job.
- Samples operating-system telemetry (CPU temperature, per-process and aggregate CPU load, live
  thread count) from a `tmux`-hosted `btop` dashboard while the job runs.
- Breaks latency down into model-load, prompt-evaluation, and generation phases, and reports
  token throughput (tokens/sec).
- Runs a RAGAS-style forensic evaluation (faithfulness / hallucination) of the generated text
  using the Groq API.
- Can run a multi-scenario benchmark suite and export an aggregate Markdown report.

The result is a single-pane dashboard showing live metrics, the streamed essay, and a
performance/hallucination verdict for the run.

## Requirements

This app depends as much on host tooling as on the JVM/SDK stack. Install the following before
running.

### Build / runtime SDK

| Requirement | Notes |
| --- | --- |
| **JDK 21** | The Gradle build pins `jvmToolchain(21)`. The [foojay resolver](https://github.com/gradle/foojay-resolver-gradle-plugin) auto-provisions JDK 21 if it is not already installed, so no manual setup is required. |
| **Gradle** | Use the bundled wrapper (`./gradlew`). No separate install needed. |
| **Kotlin / Compose** | Kotlin `2.3.x` and Compose Multiplatform `1.11.x` are managed by the version catalog (`gradle/libs.versions.toml`). |

### Host tools (Unix-like environment)

The app shells out to these at runtime, so they must be on your `PATH`:

| Tool | Required? | Used for |
| --- | --- | --- |
| `ollama` | **Required** | Launching the model server (`ollama serve`) and generation. |
| `tmux` | **Required** | Hosting the live `btop` metrics dashboard (and the GPU/telemetry pane). |
| `btop` | **Required** | Source of CPU temperature, per-core/aggregate CPU, and thread telemetry. |
| `ps`, `pgrep`, `pkill` | **Required** | Process CPU sampling and cleanup of stray Ollama/`llama-server` processes. |
| `vcgencmd` (Raspberry Pi) or `nvtop` | Optional | GPU/thermal telemetry pane. Without them the app falls back to `top`. |

> **OS support:** macOS and Linux are supported. The tooling is Unix-specific — Windows is not
> supported as-is.

### Forensic evaluation (optional)

The faithfulness/hallucination scoring calls the Groq API. It is **optional**:

- Set `GROQ_API_KEY` in your environment to enable evaluation.
- If the key is absent, the app still runs and benchmarks normally; evaluation is simply
  disabled and no network call is made.

```bash
export GROQ_API_KEY="your-groq-api-key"
```

## Running the app

Use the run configurations in your IDE's toolbar, or Gradle directly:

```bash
# Standard run
./gradlew :desktopApp:run

# Hot reload during development
./gradlew :desktopApp:hotRun --auto
```

The first run will download dependencies and (if needed) auto-provision JDK 21.

### Packaging a native installer (recommended)

This is a Compose Desktop app, so the easiest way to distribute it is with a platform installer.
Compose bundles a JRE, so end-users do **not** need JDK 21 installed.

The supported formats are configured in `desktopApp/build.gradle.kts` under
`compose.desktop.application.nativeDistributions` (DMG, MSI, Deb).

```bash
# Build the installer for the current OS
./gradlew :desktopApp:packageDistributionForCurrentOS

# Or build a specific target
./gradlew :desktopApp:packageDmg     # macOS
./gradlew :desktopApp:packageMsi     # Windows
./gradlew :desktopApp:packageDeb     # Linux / Raspberry Pi
```

The output lands under `desktopApp/build/compose/binaries/`. You can hand the installer to
anyone on the same platform; they double-click to install.

### Shadow JAR (advanced)

A fat JAR is also buildable via the Shadow plugin and is useful for CI or for platforms where
you want to supply your own JRE:

```bash
./gradlew :desktopApp:shadowJar
```

The output is `desktopApp/build/libs/desktopApp-all.jar`. The build script sets the
`Main-Class` manifest attribute to `com.codingkinetics.com.ollama_perf_monitor_desktop.MainKt`
so you can run it directly:

```bash
java -jar desktopApp/build/libs/desktopApp-all.jar
```

### Publishing to Maven Local

If you want to consume this project as a dependency in another Gradle build on the same machine,
publish the Shadow JAR to your local Maven repository:

```bash
./gradlew :desktopApp:publishToMavenLocal
```

The artifact coordinates are:
- **Group:** `com.codingkinetics`
- **Artifact:** `ollama-forensics-performance-monitoring`
- **Version:** `1.0.0`

After publishing, another project can pull it in with:

```kotlin
dependencies {
    implementation("com.codingkinetics:ollama-forensics-performance-monitoring:1.0.0")
}
```

To make this available globally, publish to Maven Central or JitPack instead of `mavenLocal()`.

## Running tests

```bash
./gradlew :shared:jvmTest
```

## Project structure

This is a Kotlin Multiplatform project. Shared logic lives under
[`shared`](./shared/src); the desktop entry point and platform-specific collectors live under
[`desktopApp`](./desktopApp/src).

- `shared/src/commonMain` — code common to all targets: domain models, the Ollama orchestration,
  the RAGAS/Groq evaluator, benchmarking, and Compose UI.
- `shared/src/jvmMain` — JVM-only implementations (e.g. the `BenchmarkRunner`).
- `desktopApp/src/main` — desktop entry point and Unix-specific collectors (`btop`/`tmux`/`ps`).

See the KDoc on the public interfaces (`BenchmarkRunner`, `MetricsCollector`, `OllamaJobRunner`)
and data models for units and semantics of the reported metrics.
