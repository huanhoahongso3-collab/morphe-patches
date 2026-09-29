extension {
    name = "extensions/spotify.mpe"
}

android {
    namespace = "app.spicetify.extension.spotify"

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("BlockedPrivateApi", "DiscouragedPrivateApi")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
