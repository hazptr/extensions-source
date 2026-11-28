import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HERO'S Web"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://heros-web.com"
    }
}
