import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ViTruyen"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://vitruyen1.com")
        }
    }

    deeplink {
        path("/..*")
    }
}
