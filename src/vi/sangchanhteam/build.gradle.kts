import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SangChanhTeam"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "vi"
        baseUrl {
            custom("https://sangchanhteam.com")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
