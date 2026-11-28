import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rolia Scan"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangataro"

    source {
        lang = "en"
        baseUrl = "https://roliascan.com"
    }
}
