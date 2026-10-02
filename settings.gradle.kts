import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "bloom-android-studio-plugin"

pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.20"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
