import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Verdinha"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "greenshit"

    source {
        lang = "pt-BR"
        baseUrl = "https://verdinha.wtf"
    }
}
