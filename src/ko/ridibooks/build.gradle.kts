import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RidiBooks"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://ridibooks.com"
    }

    deeplink {
        path("/books/..*")
    }
}
