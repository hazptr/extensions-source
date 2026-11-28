import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiLoop"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://hentailoop.com"
    }

    deeplink {
        host("hentailoop.com")
        path("/manga/..*")
    }
}
