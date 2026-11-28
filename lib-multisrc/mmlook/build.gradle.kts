plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    implementation(project(":lib:unpacker"))
}

keiyoushi {
    libVersion = "1.6"
}
