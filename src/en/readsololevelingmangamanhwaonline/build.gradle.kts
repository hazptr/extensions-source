import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Solo Leveling Manga Manhwa Online"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww4.readsololeveling.org"
    }
}
