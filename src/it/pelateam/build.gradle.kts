import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PelaTeam"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "it"
        baseUrl = "https://pelateam.org"
    }

    deeplink {
        host("pelateam.org")
        host("www.pelateam.org")
        path("/Computer.*")
        path("/Cellulare.*")
    }
}
