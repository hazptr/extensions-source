import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai.yoga"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://hentai.yoga"
    }

    deeplink {
        path("/manhwa/..*")
        path("/manga/..*")
    }
}
