plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "2.1.3"
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}

application {
    mainClass.set("org.hildan.chromium.search.ChromiumKt")
}
