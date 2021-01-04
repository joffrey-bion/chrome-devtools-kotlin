plugins {
    // Gradle doesn't support Kotlin 1.4 yet, the changes are ready but will be published in 6.8:
    // https://github.com/gradle/gradle/issues/12660
    // Using Kotlin 1.4 in scripts will only be available in Gradle 7 though:
    // https://github.com/gradle/gradle/issues/14888
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
