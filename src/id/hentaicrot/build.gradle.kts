import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Crot"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "oceanwp"

    source {
        lang = "id"
        baseUrl = "https://hentaicrot.com"
    }
}
