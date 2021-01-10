import com.jfrog.bintray.gradle.BintrayExtension.*

plugins {
    val kotlinVersion = "1.4.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version "0.10.1"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.2.3"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
    id("org.hildan.github.changelog") version "0.8.0"
}

group = "org.hildan.chrome"
description = "A Kotlin client for the Chrome DevTools Protocol"

val Project.labels: Array<String>
    get() = arrayOf("chrome", "devtools", "protocol", "chromedp", "kotlin", "coroutines", "async")

val Project.licenses: Array<String>
    get() = arrayOf("MIT")

val githubUser = getPropOrEnv("githubUser", "GITHUB_USER")
val githubRepoName = rootProject.name
val githubSlug = "$githubUser/${rootProject.name}"
val githubRepoUrl = "https://github.com/$githubSlug"

repositories {
    jcenter()
}

dependencies {
    api("org.hildan.krossbow:krossbow-websocket-core:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")

    val ktorVersion = "1.5.0"
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-serialization-jvm:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.testcontainers:testcontainers:1.15.1")
    testImplementation("org.testcontainers:junit-jupiter:1.15.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

kotlin {
    sourceSets.main {
        kotlin.srcDirs(kotlin.srcDirs + file("src/main/generated"))
    }
}

val generateProtocolApi by tasks.registering(org.hildan.chrome.devtools.build.GenerateProtocolApiTask::class)

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        dependsOn(generateProtocolApi)
    }
    dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/javadoc"
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

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifact(sourcesJar)
            artifact(dokkaJar)

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

bintray {
    user = getPropOrEnv("bintrayUser", "BINTRAY_USER")
    key = getPropOrEnv("bintrayApiKey", "BINTRAY_KEY")
    setPublications("maven")
    publish = true

    pkg(closureOf<PackageConfig> {
        repo = getPropOrEnv("bintrayRepo", "BINTRAY_REPO")
        name = project.name
        desc = project.description
        setLabels(*project.labels)
        setLicenses(*project.licenses)

        websiteUrl = githubRepoUrl
        issueTrackerUrl = "$githubRepoUrl/issues"
        vcsUrl = "$githubRepoUrl.git"
        githubRepo = githubSlug

        version(closureOf<VersionConfig> {
            desc = project.description
            vcsTag = project.version.toString()
            gpg(closureOf<GpgConfig> {
                sign = true
            })
            mavenCentralSync(closureOf<MavenCentralSyncConfig> {
                sync = true
                user = getPropOrEnv("ossrhUserToken", "OSSRH_USER_TOKEN")
                password = getPropOrEnv("ossrhKey", "OSSRH_KEY")
            })
        })
    })
}
tasks.bintrayUpload.get().dependsOn(tasks.build)

fun Project.getPropOrEnv(propName: String, envVar: String? = null): String? =
    findProperty(propName) as String? ?: System.getenv(envVar)
