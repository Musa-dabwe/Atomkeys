// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9+ has built-in Kotlin support; the kotlin-android plugin must NOT be applied.
    // The Compose compiler plugin is still separate and still required.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
