import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaOnline.fun"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangaonline.fun"
    }
}
