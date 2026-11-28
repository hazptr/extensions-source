import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HNI-Scantrad"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pizzareader"

    source {
        lang = "all"
        baseUrl = "https://hni-scantrad.net"
    }
}
