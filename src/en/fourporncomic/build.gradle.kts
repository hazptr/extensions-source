import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "4PornComic"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        lang = "en"
        baseUrl = "https://4porncomic.com"
    }

    deeplink {
        path("/comic/..*")
    }
}
