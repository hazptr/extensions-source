import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuawu"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mccms"

    source {
        name = "漫画屋"
        lang = "zh"
        baseUrl = "https://www.mhua5.com"
    }
}
