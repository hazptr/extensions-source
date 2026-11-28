import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ichijou Translations"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://ichijoutranslations.com"
        lang = "es"
    }

    deeplink {
        path("/obras/..*")
    }
}
