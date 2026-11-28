import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPoisk"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl {
            custom("https://mangapoisk.me")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
