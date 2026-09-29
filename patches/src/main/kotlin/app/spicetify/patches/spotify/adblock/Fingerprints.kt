package app.spicetify.patches.spotify.adblock

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint matching ensureIsMutable() in AbstractProtobufList.
 * By default, protobuf lists throw UnsupportedOperationException on modification.
 * Returning early allows safely removing ad sections from feeds.
 */
internal object AbstractProtobufListEnsureIsMutableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    returnType = "V",
)

/**
 * Fingerprint matching the sections getter on HomeStructure.
 */
internal object HomeStructureSectionsFingerprint : Fingerprint(
    definingClass = "/HomeStructure;",
    returnType = "Ljava/util/List;",
    parameters = emptyList(),
)

/**
 * Fingerprint matching the sections getter on BrowseStructure.
 */
internal object BrowseStructureSectionsFingerprint : Fingerprint(
    definingClass = "/BrowseStructure;",
    returnType = "Ljava/util/List;",
    parameters = emptyList(),
)

/**
 * Fingerprint matching the attributes map getter on ProductStateProto.
 */
internal object ProductStateProtoGetMapFingerprint : Fingerprint(
    definingClass = "/ProductStateProto;",
    returnType = "Ljava/util/Map;",
    parameters = emptyList(),
)

/**
 * Fingerprint matching ContextMenuViewModel initialization.
 */
internal object ContextMenuViewModelFingerprint : Fingerprint(
    strings = listOf("ContextMenuViewModel(header="),
)

/**
 * Fingerprint matching Pendragon in-app popup ad fetch request.
 */
internal object PendragonFetchMessageRequestFingerprint : Fingerprint(
    strings = listOf("FetchMessageRequest"),
)

/**
 * Fingerprint matching Pendragon in-app popup ad fetch message list request.
 */
internal object PendragonFetchMessageListRequestFingerprint : Fingerprint(
    strings = listOf("FetchMessageListRequest"),
)
