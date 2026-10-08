pluginManagement {
    repositories {
        google()
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

rootProject.name = "Aegida"

// Layers (design section 2). Imports go down only; LayerTest checks this list against each build file.
//   :app -> :alerts -> :detectors -> :core
//   :app -> :platform / :store -> :core
// :guards holds the source-scanning guard tests (design section 4); it ships nothing.
include(":core", ":detectors", ":platform", ":store", ":alerts", ":app", ":guards")
