import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ortega Scans"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://ortegascans.fr"
    }
}
