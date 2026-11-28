import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawSakura"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "rawspa"

    source {
        lang = "ja"
        baseUrl = "https://rawsakura.org"
    }

    deeplink {
        path("/title/..*")
        path("/reader/..*")
    }
}
