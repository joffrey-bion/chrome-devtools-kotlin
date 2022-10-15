plugins {
    `kotlin-dsl` // uses 1.6.10 as of Gradle 7.5
    kotlin("plugin.serialization") version "1.6.10"
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
