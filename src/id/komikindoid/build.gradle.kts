import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KomikIndoID"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "komikindo"

    source {
        lang = "id"
        baseUrl = "https://komikindo.ch"
    }
}
