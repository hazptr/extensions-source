import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InkStory"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "inkstory"

    source {
        lang = "ru"
        baseUrl = "https://inkstory.net"
        versionId = 2
    }
}
