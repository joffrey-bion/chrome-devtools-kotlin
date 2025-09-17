plugins {
    id("com.gradle.develocity") version "4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

rootProject.name = "chrome-devtools-kotlin"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = false // bad for CI, and not critical for local runs
    }
}

includeBuild("protocol-generator")
