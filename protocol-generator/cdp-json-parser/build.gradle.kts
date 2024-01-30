plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

group = "org.hildan.chrome"

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
