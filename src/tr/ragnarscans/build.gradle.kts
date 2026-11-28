import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ragnar Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl = "https://ragnarscans2.com"
        versionId = 2
    }
}
