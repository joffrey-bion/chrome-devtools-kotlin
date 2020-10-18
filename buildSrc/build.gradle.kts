plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("plugin.serialization") version "1.3.72"
}

repositories {
    mavenCentral()
    jcenter()
}

val serializationVersion = "0.20.0"

dependencies {
    implementation(gradleApi())
    implementation("com.squareup:kotlinpoet:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
}
