plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a pure-Kotlin JVM module: no Android Gradle plugin is applied, which makes an
// accidental dependency on an Android type a compile error rather than a slow test suite.
// Everything here runs on the JVM in milliseconds — this is where the correction engine lives,
// because it is the part that needs the fastest iteration loop.

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
