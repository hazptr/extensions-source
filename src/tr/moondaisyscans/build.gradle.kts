import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Moon Daisy Scans"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://moondaisyscans.pro"
    }
}
