import org.hildan.chrome.devtools.build.*
import org.jetbrains.kotlin.gradle.*
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

private val generatedProtocolSourcesDirPath = "src/commonMain/generated"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(11)

    jvm()
    js {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
        d8()
    }

    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()

    // Not supported yet by Ktor

    // wasmWasi {
    //     nodejs()
    // }

    sourceSets {
        commonMain {
            kotlin.srcDirs(generatedProtocolSourcesDirPath)

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

private val protocolDefinitionDir = layout.projectDirectory.dir("protocol-definition")

val updateProtocolDefinitions by tasks.registering(UpdateProtocolDefinitionsTask::class) {
    outputDir = protocolDefinitionDir
}

val generateProtocolApi by tasks.registering(GenerateProtocolApiTask::class) {
    protocolPaths = protocolDefinitionDir.files("browser_protocol.json", "js_protocol.json")
    targetTypesPath = protocolDefinitionDir.file("target_types.json")
    outputDirPath = project.file(generatedProtocolSourcesDirPath)
}

val printProtocolStats by tasks.registering(PrintProtocolStatsTask::class)

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
    repositories {
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
