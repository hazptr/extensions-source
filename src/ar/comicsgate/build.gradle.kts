import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicsGate"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://www.comicsgate.net"
    }

    deeplink {
        path("/home/..*")
    }
}
