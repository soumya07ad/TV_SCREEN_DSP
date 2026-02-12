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
        // Chaquopy repository
        maven { url = uri("https://chaquo.com/maven") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jitpack (required for usb-serial-for-android)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TV Screen DSP"
include(":app")
 