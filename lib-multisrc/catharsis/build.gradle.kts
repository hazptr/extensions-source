plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    libVersion = "1.6"
}

dependencies {
    api(project(":lib:cronet"))
}
