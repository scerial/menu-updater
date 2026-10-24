package com.menuupdater.weeklymenu.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A cached menu plus when it was fetched, so the resume logic can tell whether it is stale.
 * [version] lets future formats migrate; unknown JSON fields are ignored on decode.
 */
@Serializable
data class CachedMenu(
    val menu: WeeklyMenu,
    val fetchedAtEpochMs: Long = 0L,
    val version: Int = MenuCacheCodec.CURRENT_VERSION,
)

/**
 * Serializes the menu cache as versioned JSON and still decodes older payloads: a bare
 * [WeeklyMenu] (the iOS `cached-weekly-menu-v2` shape) is read as a cache with no fetch time, which
 * forces a refresh. Kept separate from Android file IO so it can be unit-tested on the JVM.
 */
object MenuCacheCodec {
    /** Matches the iOS `cached-weekly-menu-v2` lineage. */
    const val CURRENT_VERSION = 2

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(menu: WeeklyMenu, fetchedAtEpochMs: Long): String =
        json.encodeToString(CachedMenu.serializer(), CachedMenu(menu, fetchedAtEpochMs))

    fun decode(text: String): CachedMenu? {
        runCatching { json.decodeFromString(CachedMenu.serializer(), text) }
            .getOrNull()?.let { return it }
        // Fall back to a bare WeeklyMenu (older cache); treat it as stale so the next resume refreshes.
        return runCatching { json.decodeFromString(WeeklyMenu.serializer(), text) }
            .getOrNull()?.let { CachedMenu(menu = it, fetchedAtEpochMs = 0L) }
    }
}
