import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaH"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://manhwah.com"
    }

    deeplink {
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
