import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Time Square"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://mangatime-square.com"
    }
}
