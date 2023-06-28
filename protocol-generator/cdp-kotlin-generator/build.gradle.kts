plugins {
    kotlin("jvm")
}

group = "org.hildan.chrome"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":cdp-json-parser"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
}
