import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.hildan.github.changelog)
    alias(libs.plugins.hildan.kotlin.publish)
    alias(libs.plugins.vyarus.github.info)
    signing
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
    tvosSimulatorArm64()

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
                implementation(libs.kotlinx.atomicfu)

                api(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.slf4j.simple)
                implementation(libs.testcontainers.base)
                implementation(libs.testcontainers.junit.jupiter)
                implementation("org.hildan.chrome:cdp-json-parser")
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
            compileTaskProvider.configure {
                dependsOn(generateProtocolApi)
            }
        }
        val target = this
        afterEvaluate {
            tasks.named("${target.name}SourcesJar").configure {
                dependsOn(generateProtocolApi)
            }
        }
    }
}

tasks.sourcesJar {
    dependsOn(generateProtocolApi)
}

tasks.dokkaHtml {
    dependsOn(generateProtocolApi)
}

changelog {
    githubUser = github.user
    futureVersionTag = project.version.toString()
    sinceTag = "0.5.0"
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

// Resolves issues with .asc task output of the sign tasks:
// Task ':linkDebug<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
// Task ':compileTestKotlin<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
// With <platform> being one of LinuxX64, IosSimulatorArm64, MacosArm64
val problematicNativeTargets = listOf("Ios", "Macos", "Tvos", "Watchos", "Linux", "Mingw")
tasks.withType<Sign>().configureEach {
    if (problematicNativeTargets.any { it in name }) {
        val target = name.removePrefix("sign").removeSuffix("Publication")
        val linkDebugTestTaskName = "linkDebugTest$target"
        val compileTestKotlinTaskName = "compileTestKotlin$target"
        mustRunAfter(linkDebugTestTaskName, compileTestKotlinTaskName)
    }
}
