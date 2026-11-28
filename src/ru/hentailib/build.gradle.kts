import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiLib"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://hentailib.me")
        }
        lang = "ru"
    }
}
