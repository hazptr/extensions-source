import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiVNx"
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.hentaivnx.com")
        }
    }

    deeplink {
        host("www.hentaivnx.com")
        host("hentaivnx.com")
        host("www.hentaivn.college")
        host("hentaivn.college")
        path("/truyen-hentai/..*")
    }
}
