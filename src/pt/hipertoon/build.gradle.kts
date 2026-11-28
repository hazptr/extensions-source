import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hipertoon"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "hiper"

    source {
        lang = "pt-BR"
        baseUrl = "https://hipertoon.com"
    }
}
