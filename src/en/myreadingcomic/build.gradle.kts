import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyReadingComic"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        lang = "en"
        baseUrl = "https://myreadingcomic.com"
    }

    deeplink {
        path("/comic/..*")
    }
}
