import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Asmodeus Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://asmotoon.com"
        versionId = 2
    }
}
