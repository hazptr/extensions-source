import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitor Reverse"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://leitorreverse.blogspot.com"
    }
}
