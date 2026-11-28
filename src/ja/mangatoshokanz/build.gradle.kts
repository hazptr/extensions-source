import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Toshokan Z"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "マンガ図書館Z"
        lang = "ja"
        baseUrl = "https://www.mangaz.com"
    }
}
