import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Season"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "gattsu"

    source {
        lang = "pt-BR"
        baseUrl = "https://hentaiseason.com"
    }
}
