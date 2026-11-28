import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SwordMangas"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://swordmangas.com"
    }

    source {
        lang = "en"
        baseUrl = "https://swordmangas.com"
    }

    deeplink {
        path("/esp/series/..*")
        path("/esp/read/..*")
        path("/en/series/..*")
        path("/en/read/..*")
        path("/series/..*")
    }
}
