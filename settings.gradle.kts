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

rootProject.name = "LifeAssistant"
// android/          Android app
// desktop/          Windows and Linux app (shared code; platform parts in desktop/windows and desktop/linux)
// shared/cloudsync  Cloud sync used by every app
include(":android")
include(":desktop")
include(":cloudsync")
project(":cloudsync").projectDir = file("shared/cloudsync")
