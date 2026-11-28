import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18Me"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimadara"

    listOf("all", "en").forEach {
        source {
            name = "Manga18.me"
            lang = it
            baseUrl = "https://manga18.me"
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
