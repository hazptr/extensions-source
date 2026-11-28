import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Soul Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "loneseal"

    source {
        baseUrl = "https://s2.soulscans.fit"
        lang = "id"
    }
}
