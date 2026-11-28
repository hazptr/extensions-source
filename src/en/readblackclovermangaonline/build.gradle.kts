import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Black Clover Manga Online"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww10.readblackclover.com"
    }
}
