import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Feel Web"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://feelweb.jp"
    }
}
