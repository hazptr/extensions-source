import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VyvyManga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://mangavyvy.net",
                "https://mangavyvy.com",
            )
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
