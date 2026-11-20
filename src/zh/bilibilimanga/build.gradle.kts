import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BILIBILI MANGA"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "哔哩哔哩漫画"
        baseUrl = "https://manga.bilibili.com"
        lang = "zh"
    }

    deeplink {
        path("/detail/mc..*")
        path("/mc..*")
        path("/m/detail/mc..*")
        path("/m/mc..*")
    }
}
