import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikiru"
    pkgName = "id.mangatale"
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            custom("https://v1.ikiru.wtf")
        }
        versionId = 2
    }
}
