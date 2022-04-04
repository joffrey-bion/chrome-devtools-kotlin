plugins {
    val kotlinVersion = "1.6.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version "1.6.10"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.8.0"
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.hildan.github.changelog") version "1.11.1"
}

group = "org.hildan.chrome"
description = "A Kotlin client for the Chrome DevTools Protocol"

repositories {
    mavenCentral()
}

dependencies {
    api("org.hildan.krossbow:krossbow-websocket-core:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    val ktorVersion = "1.6.7"
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-serialization-jvm:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:1.7.32")
    testImplementation("org.testcontainers:testcontainers:1.16.2")
    testImplementation("org.testcontainers:junit-jupiter:1.16.2")
}

kotlin {
    sourceSets.main {
        kotlin.srcDirs(kotlin.srcDirs + file("src/main/generated"))
    }
}

val updateProtocolDefinitions by tasks.registering(org.hildan.chrome.devtools.build.UpdateProtocolDefinitionsTask::class)

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        dependsOn(generateProtocolApi)
    }
    compileTestKotlin {
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
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

            val githubUser = findProperty("githubUser") as String? ?: System.getenv("GITHUB_USER")
            val githubSlug = "$githubUser/${rootProject.name}"
            val githubRepoUrl = "https://github.com/$githubSlug"

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set(githubRepoUrl)
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("joffrey-bion")
                        name.set("Joffrey Bion")
                        email.set("joffrey.bion@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:$githubRepoUrl.git")
                    developerConnection.set("scm:git:git@github.com:$githubSlug.git")
                    url.set(githubRepoUrl)
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
