import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenZZ (unoriginal)"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://nettruyen.com.co")
        }
    }

    deeplink {
        host("nettruyen.com.co")
        host("www.nettruyen.com.co")
        path("/..*")
    }
}
