import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Valir Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        lang = "en"
        baseUrl = "https://valirscans.org"
        versionId = 4
    }
}
