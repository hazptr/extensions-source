import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rackus"
    pkgName = "en.pmscans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://rackusreads.com"
        versionId = 3
    }
}
