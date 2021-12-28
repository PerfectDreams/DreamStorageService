plugins {
    kotlin("multiplatform") version "1.5.31" apply false
    kotlin("jvm") version "1.5.31" apply false
    kotlin("plugin.serialization") version "1.5.31" apply false
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