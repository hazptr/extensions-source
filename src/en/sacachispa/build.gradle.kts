import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SACACHISPA"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://sacachispa.site"
    }

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}
