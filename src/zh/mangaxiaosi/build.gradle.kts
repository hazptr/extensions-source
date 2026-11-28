import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Xiao Si"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.jjmhw2.top",
                "https://www.jjmh.top",
                "https://mxs13.cc",
            )
        }
    }

    deeplink {
        path("/book/..*")
        path("/chapter/..*")
    }
}
