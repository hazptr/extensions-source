import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JComic"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://jcomic.net"
    }
}
