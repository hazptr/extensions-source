import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goda"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "goda"

    source {
        lang = "en"
        baseUrl = "https://manhuascans.org"
    }
}
