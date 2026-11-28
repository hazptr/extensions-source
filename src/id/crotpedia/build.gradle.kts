import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CrotPedia"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl = "https://crotpedia.net"
    }
}
