import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadRealm"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://readrealm.co"
        lang = "th"
    }

    deeplink {
        path("/comic/..*")
        path("/profile/..*")
    }
}
