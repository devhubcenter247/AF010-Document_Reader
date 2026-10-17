import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://jitpack.io")
        maven(url = "https://artifact.bytedance.com/repository/pangle/")
        maven(url = "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        google()
        maven {
            url = uri("https://maven.pkg.github.com/dbv0610/InAppUpdate")
            // GitHub Packages answers 401 without a token, even for a public package.
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
            content { includeGroup("com.github.dong0610") }
        }

        mavenCentral()
        val gprUser = providers.gradleProperty("devCenter").orNull ?: System.getenv("GITHUB_ACTOR")
        val gprKey = providers.gradleProperty("devGhpKey").orNull ?: System.getenv("GITHUB_TOKEN")
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/devhubcenter247/Lib-Ads-Application")
            credentials {
                username = gprUser
                password = gprKey
            }
            content { includeGroup("com.lib") }
        }
        maven {
            url = uri("https://maven.pkg.github.com/dbv0610/Pdf-Document-Reader")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
            content { includeGroup("com.editor") }
        }
    }
}

rootProject.name = "AF006-DocReader-Creator"
include(":app")
include(":lib")
include(":opencv")
//include(":pdfview")
