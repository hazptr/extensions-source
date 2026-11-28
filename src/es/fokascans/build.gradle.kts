import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FokaScans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://fokascans.pages.dev"
    }

    deeplink {
        path("/manga/..*")
        path("/sbs/..*")
    }
}
