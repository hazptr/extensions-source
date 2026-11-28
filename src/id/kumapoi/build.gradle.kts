import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KumaPoi"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kumapoi.info"
    }
}
