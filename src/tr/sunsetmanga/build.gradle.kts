import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunset Manga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://sunsetscans.com.tr"
        versionId = 1
    }

    deeplink {
        path("/manga/..*")
    }
}
