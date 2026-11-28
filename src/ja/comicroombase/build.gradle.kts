import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Room Base"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://comic-room-base.com"
    }
}
