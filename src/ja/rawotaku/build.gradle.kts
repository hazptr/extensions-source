import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw Otaku"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl = "https://rawotaku.com"
    }
}
