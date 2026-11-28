import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabat"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl = "https://www.mangabats.com"
    }
}
