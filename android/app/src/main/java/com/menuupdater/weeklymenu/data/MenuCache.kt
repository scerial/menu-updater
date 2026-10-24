package com.menuupdater.weeklymenu.data

import android.content.Context
import com.menuupdater.weeklymenu.domain.CachedMenu
import com.menuupdater.weeklymenu.domain.MenuCacheCodec
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import java.io.File

/**
 * File-backed menu cache (versioned JSON in the app's private storage). The serialization and
 * version tolerance live in [MenuCacheCodec]; this is just the IO.
 */
class MenuCache(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    fun load(): CachedMenu? =
        if (file.exists()) runCatching { MenuCacheCodec.decode(file.readText()) }.getOrNull() else null

    fun save(menu: WeeklyMenu, fetchedAtEpochMs: Long) {
        runCatching { file.writeText(MenuCacheCodec.encode(menu, fetchedAtEpochMs)) }
    }

    private companion object {
        const val FILE_NAME = "menu-cache.json"
    }
}
