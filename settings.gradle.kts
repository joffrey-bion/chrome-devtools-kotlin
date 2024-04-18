import de.fayard.refreshVersions.core.StabilityLevel

plugins {
    id("com.gradle.develocity") version "3.17.2"
    id("de.fayard.refreshVersions") version "0.60.5"
}

rootProject.name = "chrome-devtools-kotlin"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = false // bad for CI, and not critical for local runs
    }
}

refreshVersions {
    versionsPropertiesFile = file("build/tmp/refreshVersions/versions.properties").apply { parentFile.mkdirs() }
    rejectVersionIf {
        candidate.stabilityLevel != StabilityLevel.Stable
    }
}

includeBuild("protocol-generator")
