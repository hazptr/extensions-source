import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenCO (unoriginal)"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl = "https://nettruyenar.com"
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
