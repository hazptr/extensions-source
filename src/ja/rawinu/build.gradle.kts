import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawINU"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://rawinu.com"
    }
}
