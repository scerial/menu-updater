package com.menuupdater.weeklymenu.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.menuupdater.weeklymenu.data.MenuCache
import com.menuupdater.weeklymenu.data.MenuRepository
import com.menuupdater.weeklymenu.domain.RefreshPolicy
import com.menuupdater.weeklymenu.domain.Schedule
import com.menuupdater.weeklymenu.domain.TextSize
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import com.menuupdater.weeklymenu.ocr.OcrService
import com.menuupdater.weeklymenu.prefs.MenuPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Holds the menu and settings state and ties together the cache, repository (OCR), preferences and
 * [RefreshPolicy]. A port of the iOS `MenuViewModel`: the cache shows immediately on launch, and a
 * resume re-evaluates "today" and refreshes when the data is stale.
 */
class MenuViewModel(app: Application) : AndroidViewModel(app) {
    private val cache = MenuCache(app)
    private val preferences = MenuPreferences(app)
    private val repository = MenuRepository(recognizer = OcrService().recognizer)

    val schedule: Schedule =
        Schedule.fromJson(app.assets.open("schedule.json").bufferedReader().use { it.readText() })

    var menu by mutableStateOf<WeeklyMenu?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The instant the views treat as "now"; refreshed when the app comes to the foreground. */
    var now by mutableStateOf(LocalDateTime.now())
        private set

    private var lastFetchedEpochMs = 0L

    val defaultCafeteria: StateFlow<String> = preferences.defaultCafeteria.asState("")
    val textSize: StateFlow<TextSize> = preferences.textSize.asState(TextSize.MEDIUM)
    val buildingOrder: StateFlow<String> = preferences.buildingOrder.asState("")

    init {
        cache.load()?.let {
            menu = it.menu
            lastFetchedEpochMs = it.fetchedAtEpochMs
        }
        refresh()
    }

    /** Foreground: re-evaluate the date and reload if the menu is stale or the day changed. */
    fun didBecomeActive() {
        now = LocalDateTime.now()
        if (RefreshPolicy.shouldRefresh(lastFetchedEpochMs, System.currentTimeMillis())) refresh()
    }

    fun refresh() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = repository.fetchLatestMenu()
                menu = result
                now = LocalDateTime.now()
                val fetchedAt = System.currentTimeMillis()
                lastFetchedEpochMs = fetchedAt
                cache.save(result, fetchedAt)
            } catch (e: Throwable) {
                errorMessage = e.message ?: "메뉴를 불러오지 못했어요."
            } finally {
                isLoading = false
            }
        }
    }

    fun setDefaultCafeteria(name: String) = viewModelScope.launch { preferences.setDefaultCafeteria(name) }
    fun setTextSize(size: TextSize) = viewModelScope.launch { preferences.setTextSize(size) }
    fun setBuildingOrder(order: String) = viewModelScope.launch { preferences.setBuildingOrder(order) }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    companion object {
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
