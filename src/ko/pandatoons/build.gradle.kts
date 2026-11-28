import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PandaToons"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://pandatoons.com"
    }

    deeplink {
        path("/manhwa/..*")
        path("/read/..*")
    }
}
