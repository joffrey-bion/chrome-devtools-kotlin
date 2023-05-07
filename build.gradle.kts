import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

plugins {
    val kotlinVersion = "1.8.21"
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.atomicfu") version kotlinVersion
    id("org.jetbrains.dokka") version "1.8.10"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.1"
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.hildan.github.changelog") version "1.13.1"
    id("org.hildan.kotlin-publish") version "1.1.0"
    id("ru.vyarus.github-info") version "1.5.0"
}

group = "org.hildan.chrome"
description = "A Kotlin client for the Chrome DevTools Protocol"

github {
    user = "joffrey-bion"
    license = "MIT"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    mingwX64()
    linuxX64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    tvosX64()
    tvosArm64()

    // Not supported yet by all dependencies
    // linuxArm64()         missing in Ktor, coroutines
    // watchosDeviceArm64() missing in Ktor, coroutines, serialization
    // androidNativeArm32() missing in Ktor, coroutines, serialization
    // androidNativeArm64() missing in Ktor, coroutines, serialization
    // androidNativeX64()   missing in Ktor, coroutines, serialization
    // androidNativeX86()   missing in Ktor, coroutines, serialization

    sourceSets {
        commonMain {
            kotlin.srcDirs("src/commonMain/generated")

            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation("org.jetbrains.kotlinx:atomicfu:0.20.2")

                api(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.cio)
                implementation(libs.slf4j.simple)
                implementation(libs.testcontainers.base)
                implementation(libs.testcontainers.junit.jupiter)
            }
        }
    }
}

val updateProtocolDefinitions by tasks.registering(org.hildan.chrome.devtools.build.UpdateProtocolDefinitionsTask::class)

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

val printProtocolStats by tasks.registering(org.hildan.chrome.devtools.build.PrintProtocolStatsTask::class)

tasks.named<KotlinJvmTest>("jvmTest") {
    useJUnitPlatform()
}

kotlin {
    targets.all {
        compilations.all {
            tasks.named(compileKotlinTaskName).configure {
                dependsOn(generateProtocolApi)
            }
        }
    }
}

changelog {
    githubUser = github.user
    futureVersionTag = project.version.toString()
    sinceTag = "0.5.0"
}

tasks.dokkaHtml {
    dependsOn(generateProtocolApi)
}

nexusPublishing {
    packageGroup.set("org.hildan")
    this.repositories {
        sonatype()
    }
}

publishing {
    // configureEach reacts on new publications being registered and configures them too
    publications.configureEach {
        if (this is MavenPublication) {
            pom {
                developers {
                    developer {
                        id.set("joffrey-bion")
                        name.set("Joffrey Bion")
                        email.set("joffrey.bion@gmail.com")
                    }
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(extensions.getByType<PublishingExtension>().publications)
}
