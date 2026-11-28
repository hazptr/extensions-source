import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaGalaxy"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        lang = "en"
        baseUrl = "https://manhwagalaxy.com"
    }

    deeplink {
        path("/manhwa/..*")
    }
}
