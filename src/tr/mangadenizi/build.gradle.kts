import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDenizi"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        path("/manga/..*")
    }

    source {
        lang = "tr"
        baseUrl = "https://mangadenizi.net"
    }
}
