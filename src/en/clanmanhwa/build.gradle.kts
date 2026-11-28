import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ClanManhwa"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zinmangaart"

    source {
        baseUrl = "https://www.clanmanhwa.com"
        lang = "en"
    }
}
