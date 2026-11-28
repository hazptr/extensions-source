import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Susunova"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://susunova.xyz"
    }

    deeplink {
        path("/manga/..*")
    }
}
