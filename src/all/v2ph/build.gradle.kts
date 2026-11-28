import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "V2PH"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://www.v2ph.com"
    }

    deeplink {
        path("/album/..*")
    }
}
