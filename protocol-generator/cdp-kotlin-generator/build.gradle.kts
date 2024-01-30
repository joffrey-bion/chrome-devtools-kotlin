plugins {
    kotlin("jvm")
}

group = "org.hildan.chrome"

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":cdp-json-parser"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
}
