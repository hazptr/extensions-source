import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Anime GDR Club"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "it"
        baseUrl = "http://www.agcscanlation.it"
    }
}
