import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sinergia sin control"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://sinergiasincontrol.blogspot.com"
    }

    deeplink {
        path("/")
        path("/..*/..*/..*.html")
    }
}
