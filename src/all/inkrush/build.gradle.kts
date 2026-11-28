import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "INKRUSH"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://inkrush.site"
    }

    source {
        lang = "id"
        baseUrl = "https://inkrush.site"
    }

    deeplink {
        path("/comics/..*")
    }
}
