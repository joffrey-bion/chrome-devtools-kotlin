plugins {
    val kotlinVersion = "1.8.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version "1.8.10"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.1"
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.hildan.github.changelog") version "1.13.1"
    id("ru.vyarus.github-info") version "1.4.0"
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

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    api(libs.ktor.client.cio)
    api(libs.ktor.client.contentNegotiation)
    api(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.testcontainers.base)
    testImplementation(libs.testcontainers.junit.jupiter)
}

kotlin {
    sourceSets.main {
        kotlin.srcDirs(kotlin.srcDirs + file("src/main/generated"))
    }
}

val updateProtocolDefinitions by tasks.registering(org.hildan.chrome.devtools.build.UpdateProtocolDefinitionsTask::class)

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

val printProtocolStats by tasks.registering(org.hildan.chrome.devtools.build.PrintProtocolStatsTask::class)

tasks {
    compileKotlin {
        dependsOn(generateProtocolApi)
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("failed", "standardOut", "standardError")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
        }
    }
}

changelog {
    githubUser = github.user
    futureVersionTag = project.version.toString()
    sinceTag = "0.5.0"
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn(generateProtocolApi)
}

tasks.dokkaJavadoc {
    dependsOn(generateProtocolApi)
}

val dokkaJavadocJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka into a Javadoc jar"
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

nexusPublishing {
    packageGroup.set("org.hildan")
    repositories {
        sonatype()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifact(sourcesJar)
            artifact(dokkaJavadocJar)

            pom {
                name.set(project.name)
                description.set(project.description)
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
    sign(publishing.publications["maven"])
}
