import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lezhin Comics"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://www.lezhin.com"
    }

    deeplink {
        path("/ko/comic/..*")
    }
}
