import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mokuro"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        host("mokuro.moe")
        path("/catalog.*")
        path("/mokuro-reader/..*")
    }

    source {
        lang = "ja"
        baseUrl = "https://mokuro.moe"
    }
}
