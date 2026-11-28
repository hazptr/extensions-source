import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ken Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "iken"

    source {
        baseUrl = "https://kencomics.com"
        lang = "en"
    }
}
