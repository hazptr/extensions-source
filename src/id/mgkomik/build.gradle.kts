import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MG Komik"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://web1.mgkomik.cc"
        versionId = 2
    }
}
