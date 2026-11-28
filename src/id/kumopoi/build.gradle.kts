import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KumoPoi"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "aesir"

    source {
        lang = "id"
        baseUrl {
            custom("https://beta.kumopoi.com")
        }
    }

    deeplink {
        host("kumopoi.com")
        path("/comic/..*")
    }
}
