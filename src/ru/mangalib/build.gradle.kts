import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLib"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://mangalib.me")
        }
        lang = "ru"
    }
}
