import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Hindi Manga"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://readhindimanga.app"
        lang = "hi"
    }

    deeplink {
        path("/manga/..*")
    }
}
