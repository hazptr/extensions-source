import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Scan Reader"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "scanreader"

    source {
        lang = "fr"
        baseUrl = "https://hentai.scanreader.net"
    }
}
