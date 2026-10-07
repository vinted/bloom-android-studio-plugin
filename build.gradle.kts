import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.vinted.bloom"
version = "0.1.22"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localAndroidStudioPath = providers.gradleProperty("androidStudioPath").orNull
        if (localAndroidStudioPath != null) {
            local(localAndroidStudioPath)
        } else {
            androidStudio(providers.gradleProperty("androidStudioVersion").get())
        }
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "999.*"
        }
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
}
