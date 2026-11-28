import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aethon Webcomics"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://aethonwebcomics.com"
        lang = "en"
    }

    deeplink {
        path("/webcomics/..*")
        path("/comic-episode/..*")
    }
}
