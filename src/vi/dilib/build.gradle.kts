import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dilib"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://dilib.vn")
        }
    }

    deeplink {
        path("/..*.html")
        path("/truyen-tranh/..*.html")
    }
}
