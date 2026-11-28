import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ranker-Manga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zmanga"

    source {
        lang = "th"
        baseUrl = "https://www.ranker-manga.com"
    }

    deeplink {
        host("www.ranker-manga.com")
        host("ranker-manga.com")
        path("/..*")
    }
}
