plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    libVersion = "1.6"

    deeplink {
        path("/read/..*")
        path("/manga/..*")
        path("/raw-manga/..*")
    }
}
