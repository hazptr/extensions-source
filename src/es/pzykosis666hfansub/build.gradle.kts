import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pzykosis666HFansub"
    theme = "foolslide"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://lector.pzykosis666hfansub.com"
        lang = "es"
    }
}
