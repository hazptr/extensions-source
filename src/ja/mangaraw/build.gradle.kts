import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaRaw"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://mangaraw.co.uk"
        lang = "ja"
    }

    deeplink {
        path("/manga/..*")
    }
}
