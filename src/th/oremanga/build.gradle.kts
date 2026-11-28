import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OreManga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zmanga"

    source {
        lang = "th"
        baseUrl = "https://www.oremanga.net"
    }
}
