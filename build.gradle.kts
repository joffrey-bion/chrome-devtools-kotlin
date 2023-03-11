plugins {
    val kotlinVersion = "1.7.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version kotlinVersion
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.11.1"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    val ktorVersion = "2.2.4"
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:2.0.3")
    testImplementation("org.testcontainers:testcontainers:1.17.4")
    testImplementation("org.testcontainers:junit-jupiter:1.17.4")
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
