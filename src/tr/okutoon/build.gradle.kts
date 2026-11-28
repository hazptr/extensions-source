import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OkuToon"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://okutoon.net"
    }

    deeplink {
        host("okutoon.net")
        host("www.okutoon.net")
        host("okutoon.com")
        path("/..*")
    }
}
