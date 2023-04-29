plugins {
    `kotlin-dsl` // uses Kotlin API level 1.8.10 as of Gradle 8.0 (and 8.1)
    kotlin("plugin.serialization") version "1.8.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("com.squareup:kotlinpoet:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
}

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs = listOf("-Xinline-classes")
    }
}
