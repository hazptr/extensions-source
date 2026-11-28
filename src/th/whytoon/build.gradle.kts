import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WhyToon"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://whytoon.com"
    }

    deeplink {
        host("whytoon.com")
        host("www.whytoon.com")
        path("/content/..*")
        path("/browse.*")
    }
}
