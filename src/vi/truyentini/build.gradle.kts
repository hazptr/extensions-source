import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenTini"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyentini.net")
        }
    }
    deeplink {
        path("/truyen/..*")
    }
}
