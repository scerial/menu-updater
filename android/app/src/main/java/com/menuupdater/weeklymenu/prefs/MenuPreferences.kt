package com.menuupdater.weeklymenu.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.menuupdater.weeklymenu.domain.TextSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "menu-prefs")

/**
 * User settings backed by DataStore: the default cafeteria to open on, the text-size tier, and the
 * saved building order for the 운영 시간 list. Text size is stored as the stable raw value (-1/0/1);
 * an unknown value becomes [TextSize.MEDIUM].
 */
class MenuPreferences(private val context: Context) {
    val defaultCafeteria: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_CAFETERIA].orEmpty() }

    val textSize: Flow<TextSize> =
        context.dataStore.data.map { TextSize.fromRaw(it[Keys.TEXT_SIZE]) }

    /** Comma-separated building names; empty means the default order. */
    val buildingOrder: Flow<String> =
        context.dataStore.data.map { it[Keys.BUILDING_ORDER].orEmpty() }

    suspend fun setDefaultCafeteria(cafeteria: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CAFETERIA] = cafeteria }
    }

    suspend fun setTextSize(size: TextSize) {
        context.dataStore.edit { it[Keys.TEXT_SIZE] = size.raw }
    }

    suspend fun setBuildingOrder(order: String) {
        context.dataStore.edit { it[Keys.BUILDING_ORDER] = order }
    }

    private object Keys {
        val DEFAULT_CAFETERIA = stringPreferencesKey("default-cafeteria")
        val TEXT_SIZE = intPreferencesKey("text-size")
        // Matches the iOS AppStorage key name for continuity.
        val BUILDING_ORDER = stringPreferencesKey("venue-building-order")
    }
}
