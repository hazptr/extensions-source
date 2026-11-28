import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Evil production"
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "cs"
        baseUrl = "https://evil-manga.eu"
    }
}
