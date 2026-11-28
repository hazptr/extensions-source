import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ainz Scans ID"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "loneseal"

    source {
        lang = "id"
        baseUrl = "https://v3.ainzscans01.com"
    }
}
