plugins {
    `kotlin-dsl` // uses Kotlin API level 1.8 as of Gradle 8.0
    kotlin("plugin.serialization") version "1.8.20"
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
