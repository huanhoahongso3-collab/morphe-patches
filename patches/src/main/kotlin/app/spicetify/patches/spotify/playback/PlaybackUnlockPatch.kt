package app.spicetify.patches.spotify.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.adblock.ProductStateProtoGetMapFingerprint
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_PLAYBACK_CLASS = "Lapp/spicetify/extension/spotify/playback/PlaybackUnlocker;"

@Suppress("unused")
val playbackUnlockPatch = bytecodePatch(
    name = "Unlock playback",
    description = "Unlocks playback restrictions including unlimited skips, shuffle mode, on-demand track selection, and audio ad suppression without modifying account license attributes.",
    default = true,
) {
    compatibleWith(spotifyCompatibility)
    extendWith("extensions/spotify.mpe")

    execute {
        ProductStateProtoGetMapFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val igetIndex = instructions.indexOfFirst { it.opcode == Opcode.IGET_OBJECT }
            if (igetIndex >= 0) {
                val register = (instructions[igetIndex] as TwoRegisterInstruction).registerA
                val alreadyInjected = instructions.any { inst ->
                    inst.toString().contains("PlaybackUnlocker")
                }
                if (!alreadyInjected) {
                    method.addInstructions(
                        igetIndex + 1,
                        "invoke-static { v$register }, $EXTENSION_PLAYBACK_CLASS->unlockPlaybackWithoutPremiumToggle(Ljava/util/Map;)V"
                    )
                }
            }
        }
    }
}
