import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiScan (Unoriginal)"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "yiimanga"

    source {
        lang = "en"
        baseUrl = "https://yaoiscan.me"
    }

    deeplink {
        path("/read/..*")
    }
}
