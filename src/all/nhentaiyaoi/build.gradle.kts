import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "nHentai Yaoi"
    theme = "madara"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://nhentaiyaoi.com"
        lang = "all"
    }

    deeplink {
        host("nhentaiyaoi.com")
        host("www.nhentaiyaoi.com")
        path("/.*/..*")
    }
}
