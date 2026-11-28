import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pix Hentai"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "oceanwp"

    source {
        lang = "id"
        baseUrl = "https://pixhentai.com"
    }
}
