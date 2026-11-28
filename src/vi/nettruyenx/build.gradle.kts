import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenX (unoriginal)"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl = "https://nettruyenx.net"
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
