pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AtomKeys"

// See .planning/development-roadmap.md § "Target module graph".
//   :engine  pure Kotlin JVM — no Android deps, so its tests run in milliseconds
//   :design  tokens, themes, typography, sizing scale
//   :ime     InputMethodService, keyboard UI, touch handling, InputConnection
//   :app     settings + setup wizard; depends on all three
include(":app")
include(":ime")
include(":design")
include(":engine")
