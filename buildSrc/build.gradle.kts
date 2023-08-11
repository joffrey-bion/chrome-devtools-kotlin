plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
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
