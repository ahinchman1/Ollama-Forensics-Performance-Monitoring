import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation("com.github.netguru.compose-multiplatform-charts:charts-desktop:1.0.0-alpha03")
}

compose.desktop {
    application {
        mainClass = "com.codingkinetics.com.ollama_perf_monitor_desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.codingkinetics.com.ollama_perf_monitor_desktop"
            packageVersion = "1.0.0"
        }
    }
}