plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.cloud.tools.jib") version Versions.JIB
}

group = "net.perfectdreams.dreamstorageservice"
version = Versions.DREAM_STORAGE_SERVICE

dependencies {
    implementation(project(":common"))
    implementation(kotlin("stdlib"))
    implementation("net.perfectdreams.sequins.ktor:base-route:1.0.4")
    implementation("io.ktor:ktor-server-netty:${Versions.KTOR}")
    implementation("io.ktor:ktor-server-compression:${Versions.KTOR}")
    implementation("io.ktor:ktor-server-caching-headers:${Versions.KTOR}")
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha14")
    implementation("commons-codec:commons-codec:1.15")

    // Databases
    implementation("org.jetbrains.exposed:exposed-core:0.37.3")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.37.3")
    implementation("org.jetbrains.exposed:exposed-dao:0.37.3")
    implementation("org.jetbrains.exposed:exposed-java-time:0.37.3")
    implementation("org.postgresql:postgresql:42.3.3")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("io.github.microutils:kotlin-logging:2.1.21")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.6")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.KOTLINX_SERIALIZATION}")

    testImplementation("io.ktor:ktor-server-tests:${Versions.KTOR}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.5.31")
}

jib {
    to {
        image = "ghcr.io/perfectdreams/dreamstorageservice"

        auth {
            username = System.getProperty("DOCKER_USERNAME") ?: System.getenv("DOCKER_USERNAME")
            password = System.getProperty("DOCKER_PASSWORD") ?: System.getenv("DOCKER_PASSWORD")
        }
    }

    from {
        // This image comes from the "docker" folder Dockerfile!
        // Don't forget to build the image before compiling DreamStorageService!
        // https://github.com/GoogleContainerTools/jib/issues/1468
        image = "tar://${File(rootDir, "docker/image.tar").absoluteFile}"
    }
}