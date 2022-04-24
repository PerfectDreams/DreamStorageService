plugins {
    kotlin("multiplatform") version Versions.KOTLIN apply false
    kotlin("jvm") version Versions.KOTLIN apply false
    kotlin("plugin.serialization") version Versions.KOTLIN apply false
    id("maven-publish")
}

group = "net.perfectdreams.dreamstorageservice"
version = Versions.DREAM_STORAGE_SERVICE

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.perfectdreams.net/")
    }
}

subprojects {
    apply<MavenPublishPlugin>()
    group = "net.perfectdreams.dreamstorageservice"
    version = Versions.DREAM_STORAGE_SERVICE

    publishing {
        repositories {
            maven {
                name = "PerfectDreams"
                url = uri("https://repo.perfectdreams.net/")
                credentials(PasswordCredentials::class)
            }
        }
    }
}