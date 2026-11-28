import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cin.blue"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ja", "zh").forEach { language ->
        source {
            lang = language
            baseUrl = "https://cin.blue"
        }
    }

    deeplink {
        path("/v/..*")
        path("/g/..*")
    }
}
