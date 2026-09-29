package app.spicetify.patches.spotify.adblock

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_ADBLOCK_CLASS = "Lapp/spicetify/extension/spotify/adblock/AdBlocker;"

@Suppress("unused")
val blockAdsPatch = bytecodePatch(
    name = "Block ads",
    description = "Blocks banner, pop-up, home feed, and browse ads around the app without modifying audio playback.",
    default = true,
) {
    compatibleWith(spotifyCompatibility)
    extendWith("extensions/spotify.mpe")

    execute {
        // 1. Allow Protobuf list mutation so ad sections can be removed from feed structures.
        AbstractProtobufListEnsureIsMutableFingerprint.matchAllOrNull()?.forEach { match ->
            val instructions = match.method.implementation?.instructions?.toList().orEmpty()
            val throwsUnsupported = instructions.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()
                    ?.contains("UnsupportedOperationException") == true
            }
            if (throwsUnsupported) {
                match.method.addInstructions(0, "return-void")
            }
        }

        // 2. Remove Home feed video and image brand ad sections.
        HomeStructureSectionsFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val igetIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference?.let { ref ->
                        (ref as? FieldReference)?.name == "sections_"
                    } == true
            }
            val targetIndex = if (igetIndex >= 0) igetIndex else instructions.indexOfFirst { it.opcode == Opcode.IGET_OBJECT }
            if (targetIndex >= 0) {
                val register = (instructions[targetIndex] as TwoRegisterInstruction).registerA
                method.addInstructions(
                    targetIndex + 1,
                    "invoke-static { v$register }, $EXTENSION_ADBLOCK_CLASS->removeHomeSections(Ljava/util/List;)V"
                )
            }
        }

        // 3. Remove Browse feed brand ads.
        BrowseStructureSectionsFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val igetIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference?.let { ref ->
                        (ref as? FieldReference)?.name == "sections_"
                    } == true
            }
            val targetIndex = if (igetIndex >= 0) igetIndex else instructions.indexOfFirst { it.opcode == Opcode.IGET_OBJECT }
            if (targetIndex >= 0) {
                val register = (instructions[targetIndex] as TwoRegisterInstruction).registerA
                method.addInstructions(
                    targetIndex + 1,
                    "invoke-static { v$register }, $EXTENSION_ADBLOCK_CLASS->removeBrowseSections(Ljava/util/List;)V"
                )
            }
        }

        // 4. Override display/visual ads in ProductState attributes while leaving audio ads untouched.
        ProductStateProtoGetMapFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val igetIndex = instructions.indexOfFirst { it.opcode == Opcode.IGET_OBJECT }
            if (igetIndex >= 0) {
                val register = (instructions[igetIndex] as TwoRegisterInstruction).registerA
                method.addInstructions(
                    igetIndex + 1,
                    "invoke-static { v$register }, $EXTENSION_ADBLOCK_CLASS->overrideAdAttributes(Ljava/util/Map;)V"
                )
            }
        }

        // 5. Filter context menu items to remove "Listen to music ad-free" and promotional upsells.
        ContextMenuViewModelFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val listParamIndex = method.parameters.indexOfFirst { it.type == "Ljava/util/List;" }
            if (listParamIndex >= 0) {
                val paramRegister = listParamIndex + 1
                method.addInstructions(
                    0,
                    """
                        invoke-static { p$paramRegister }, $EXTENSION_ADBLOCK_CLASS->filterContextMenuItems(Ljava/util/List;)Ljava/util/List;
                        move-result-object p$paramRegister
                    """.trimIndent()
                )
            }
        }

        // 6. Disable Pendragon in-app popup ad requests.
        val pendragonMatches = PendragonFetchMessageRequestFingerprint.matchAllOrNull().orEmpty() +
            PendragonFetchMessageListRequestFingerprint.matchAllOrNull().orEmpty()
        pendragonMatches.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            // If method constructs or maps the request, short-circuit to prevent displaying popup ads
            val newInstanceIndex = instructions.indexOfFirst { it.opcode == Opcode.NEW_INSTANCE }
            if (newInstanceIndex >= 0) {
                val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
                if (returnIndex > newInstanceIndex) {
                    val returnReg = (instructions[returnIndex] as OneRegisterInstruction).registerA
                    method.replaceInstruction(newInstanceIndex, "const/4 v$returnReg, 0")
                }
            }
        }
    }
}
