import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nirvana Manga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://nirvanamanga.com"
    }
}
