import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team Lanh Lung"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "koi"

    source {
        name = "Team Lạnh Lùng"
        lang = "vi"
        baseUrl {
            custom("https://lanhlungteam3.top")
        }
    }
}
