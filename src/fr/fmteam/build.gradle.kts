import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FMTEAM"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pizzareader"

    source {
        lang = "fr"
        baseUrl = "https://fmteam.fr"
        versionId = 2
    }
}
