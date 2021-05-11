plugins {
    `kotlin-dsl` // uses 1.4.31 as of Gradle 7.0
    kotlin("plugin.serialization") version "1.4.31"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("com.squareup:kotlinpoet:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
}
