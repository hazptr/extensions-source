import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeoSSS"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "vi"
        baseUrl {
            custom("https://meosss.com")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
