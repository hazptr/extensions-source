import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doge Manga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫畫狗"
        lang = "zh"
        baseUrl = "https://dogemanga.com"
    }

    deeplink {
        path("/m/..*")
    }
}
