import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Speed Manga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://speed-manga.com"
    }
}
