plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("plugin.serialization") version "1.4.20"
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(gradleApi())
    implementation("com.squareup:kotlinpoet:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
}
