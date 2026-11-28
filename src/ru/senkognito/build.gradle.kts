import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Senkognito"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "senkuro"

    source {
        baseUrl {
            mirrors(
                "Россия" to "https://senkuro.me",
                "Публичный" to "https://senkognito.com",
            )
        }
        lang = "ru"
    }
}
