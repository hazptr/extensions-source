import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicaso"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            mirrors(
                "https://comicaso.co",
                "https://comicaso.cc",
                "https://comicaso.one",
            )
        }
        versionId = 2
    }

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}
