import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hiperdex"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "hiper"

    source {
        lang = "en"
        baseUrl {
            custom("https://hiperdex.tv")
        }
    }
}
