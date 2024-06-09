plugins {
    `kotlin-dsl`
    // Can't use embeddedKotlinVersion for serialization yet because kotlinx-serialization-json requires Kotlin >= 2.0
    // kotlin("plugin.serialization") version embeddedKotlinVersion
    alias(libs.plugins.kotlin.serialization)
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
