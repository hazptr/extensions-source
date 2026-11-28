import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hijala Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "iken"

    source {
        baseUrl = "https://en-hijala.com"
        lang = "en"
    }
}
