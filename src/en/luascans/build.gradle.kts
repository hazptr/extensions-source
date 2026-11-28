import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lua Scans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "heancms"

    source {
        lang = "en"
        baseUrl = "https://luacomic.org"
        // Moved from Keyoapp to HeanCms
        versionId = 3
    }
}
