plugins {
    val kotlinVersion = "1.4.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version "0.10.1"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.2.3"
}

version = "0.1.0"
description = "A Kotlin client for the Chrome DevTools Protocol"

repositories {
    jcenter()
}

dependencies {
    implementation("org.hildan.krossbow:krossbow-websocket-core:0.43.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")

    val ktorVersion = "1.4.0"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

kotlin {
    sourceSets.main {
        kotlin.srcDirs(kotlin.srcDirs + file("src/main/generated"))
    }
}

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        dependsOn(generateProtocolApi)
    }
    dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/javadoc"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("failed", "standardOut", "standardError")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
        }
    }
}

dependencies {
    implementation("org.hildan.krossbow:krossbow-websocket-core:0.43.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")

    val ktorVersion = "1.4.0"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

tasks.compileKotlin {
    dependsOn(generateProtocolApi)
}
