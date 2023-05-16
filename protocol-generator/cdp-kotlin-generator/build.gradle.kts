plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
}

group = "org.hildan.chrome"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":cdp-json-parser"))
    implementation("com.squareup:kotlinpoet:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}
