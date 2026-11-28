import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CManhua"
    pkgName = "en.cmanhua"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://cmanhua.com"
        versionId = 2
    }
}
