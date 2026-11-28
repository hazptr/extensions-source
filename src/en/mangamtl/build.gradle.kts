import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga MTL"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "manhuarm"

    source {
        lang = "en"
        baseUrl = "https://mtlmanga.org"
    }
}
