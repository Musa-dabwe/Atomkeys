// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9+ has built-in Kotlin support; the kotlin-android plugin must NOT be applied.
    // The Compose compiler plugin is still separate and still required.
    // Every plugin a subproject applies must be declared here with `apply false`, so it lands on the
    // build classpath exactly once. Requesting a version in a subproject when the plugin is already
    // on the classpath fails with "already on the classpath with an unknown version".
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
