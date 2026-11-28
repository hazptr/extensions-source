import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnzManga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mmrcms"

    source {
        lang = "es"
        baseUrl = "https://www.anzmanga25.com"
    }
}
