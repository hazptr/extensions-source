import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas Brasuka"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "aurora"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangasbrasuka.org"
        versionId = 2
    }
}
