import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tenshi Manga"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://tenshimanga.com"
        versionId = 2
    }
}
