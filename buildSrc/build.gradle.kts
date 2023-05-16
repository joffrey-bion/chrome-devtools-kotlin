plugins {
    `kotlin-dsl` // uses Kotlin API level 1.8.10 as of Gradle 8.0 (and 8.1)
    kotlin("plugin.serialization") version "1.8.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.hildan.chrome:cdp-kotlin-generator")
    implementation("org.hildan.chrome:cdp-json-parser")
    implementation(libs.kotlinx.serialization.json)
}
