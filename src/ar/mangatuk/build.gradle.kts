import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTuk"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://mangatuk.com"
        versionId = 2
    }
}
