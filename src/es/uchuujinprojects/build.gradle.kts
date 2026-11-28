import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Uchuujin Projects"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://uchuujinmangas.com"
    }
}
