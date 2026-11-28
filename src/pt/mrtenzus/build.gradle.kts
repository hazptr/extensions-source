import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MR Tenzus"
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://mrtenzus.com"
    }
}
