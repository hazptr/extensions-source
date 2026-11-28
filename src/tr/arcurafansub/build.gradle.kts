import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arcura Fansub"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://arcurafansub.com"
    }
}
