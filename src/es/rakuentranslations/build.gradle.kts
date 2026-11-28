import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rakuen Translations"
    theme = "mangathemesia"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://rakuentranslations.com"
        lang = "es"
        versionId = 2
    }
}
