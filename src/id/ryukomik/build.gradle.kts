import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ryukomik"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://ryukomik.com"
        lang = "id"
    }

    deeplink {
        path("/komik/.*")
    }
}
