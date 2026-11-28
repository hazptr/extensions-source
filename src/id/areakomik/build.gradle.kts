import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Area Komik"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://areakomik.com"
        lang = "id"
    }

    deeplink {
        path("/series/..*")
        path("/chapter/..*")
    }
}
