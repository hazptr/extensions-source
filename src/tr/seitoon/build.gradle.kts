import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Seitoon"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://seitoon.com.tr"
    }

    deeplink {
        host("seitoon.com.tr")
        host("www.seitoon.com.tr")
        path("/comics/..*")
        path("/series/..*")
    }
}
