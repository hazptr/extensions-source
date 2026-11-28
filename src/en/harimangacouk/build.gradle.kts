import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HariManga.co.uk"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zinmangaart"

    source {
        baseUrl = "https://www.harimanga.co.uk"
        lang = "en"
    }
}
