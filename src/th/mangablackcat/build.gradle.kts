import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBlackCat"
    pkgName = "th.onemanga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://mangablackcat.com"
        id = 2248402620929558947L
    }
}
