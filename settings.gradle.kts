import de.fayard.refreshVersions.core.StabilityLevel

plugins {
    id("com.gradle.enterprise") version "3.16.2"
    id("de.fayard.refreshVersions") version "0.60.5"
}

rootProject.name = "chrome-devtools-kotlin"

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}

refreshVersions {
    versionsPropertiesFile = file("build/tmp/refreshVersions/versions.properties").apply { parentFile.mkdirs() }
    rejectVersionIf {
        candidate.stabilityLevel != StabilityLevel.Stable
    }
}

includeBuild("protocol-generator")
