import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GoodToon"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "ko"
        baseUrl {
            custom("https://www.goodtoon010.com")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
