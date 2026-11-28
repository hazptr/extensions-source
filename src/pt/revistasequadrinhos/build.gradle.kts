import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Revistas e Quadrinhos"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt"
        baseUrl = "https://revistasequadrinhos.com"
    }
}
