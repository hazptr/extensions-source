import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPanda.onl"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangapanda.onl"
    }
}
