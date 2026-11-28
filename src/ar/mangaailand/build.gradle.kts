import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ai Land"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://manga-ai-land.blogspot.com"
    }
}
