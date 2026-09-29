package app.spicetify.patches.spotify

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

val spotifyCompatibility = Compatibility(
    name = "Spotify",
    packageName = "com.spotify.music",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x1DB954,
    targets = emptyList(),
)
