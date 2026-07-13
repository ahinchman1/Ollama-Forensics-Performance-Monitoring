import org.gradle.api.file.DuplicatesStrategy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    `maven-publish`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.shadow.jar)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:${libs.versions.kotlin.get()}"))

    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlin.test)
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

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "com.codingkinetics.com.ollama_perf_monitor_desktop.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles()

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/MANIFEST.MF")

    exclude("META-INF/LICENSE")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/LICENSE.md")
    exclude("META-INF/NOTICE")
    exclude("META-INF/NOTICE.txt")
    exclude("META-INF/NOTICE.md")
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["shadow"])
            groupId = "com.codingkinetics"
            artifactId = "ollama-forensics-performance-monitoring"
            version = "1.0.0"
        }
    }
}