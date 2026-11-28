import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elder Manga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://eldermanga.com"
        versionId = 2
    }
}
