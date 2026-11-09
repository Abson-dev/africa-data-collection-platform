plugins {
    // Lets Gradle download the JDK set in jvmToolchain() if it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "africa-data-collection-platform"

include(":libs:sms-codec")
include(":services:bridge")
