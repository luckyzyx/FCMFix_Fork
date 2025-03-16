enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        google()
        mavenCentral()
    }
}

include(":app")
rootProject.name = "fcmfix"