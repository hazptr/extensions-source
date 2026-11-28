import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-shi"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://manga-shi.org"
    }

    deeplink {
        path("/manga/..*")
    }
}
