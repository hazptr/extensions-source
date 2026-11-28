import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NIFTeam"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "foolslide"

    source {
        lang = "it"
        baseUrl = "https://read-nifteam.info"
    }
}
