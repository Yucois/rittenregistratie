import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

// Bytecode op niveau 17, zodat de Android-module deze module kan gebruiken,
// zonder een vaste toolchain te eisen: bouwen kan met elke JDK vanaf 17.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
