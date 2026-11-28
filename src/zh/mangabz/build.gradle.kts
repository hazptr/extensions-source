import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabz"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl {
            mirrors(
                "mangabz.com" to "https://mangabz.com",
                "xmanhua.com" to "https://xmanhua.com",
                "yymanhua.com" to "https://yymanhua.com",
                "yymanhua.com (HTTP, 中国大陆)" to "http://yymanhua.com",
            )
        }
    }

    deeplink {
        host("mangabz.com")
        host("xmanhua.com")
        host("yymanhua.com")
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
