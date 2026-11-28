import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "vomic"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://vomicmh.com"
        versionId = 2
    }

    deeplink {
        path("/detail/..*")
    }
}
