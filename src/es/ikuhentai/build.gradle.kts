import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikuhentai"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://ikuhentai.net"
    }
}
