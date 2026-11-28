import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toontaku"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://toontaku.com"
        lang = "tr"
    }

    deeplink {
        path("/seri/..*")
    }
}
