plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    libVersion = "1.6"

    deeplink {
        path("/manga/..*")
        path("/chapter/..*")
    }
}
