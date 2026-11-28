import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NhatTruyen"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl = "https://nhattruyenqq.com"
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
