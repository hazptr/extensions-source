import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HongTraTeam"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://hongtrateam.com")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
