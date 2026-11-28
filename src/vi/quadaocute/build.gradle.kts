import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "QuaDaoCute"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://qadcuteo.cc")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
