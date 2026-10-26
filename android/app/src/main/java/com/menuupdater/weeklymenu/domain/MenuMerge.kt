package com.menuupdater.weeklymenu.domain

/**
 * A refresh can come back with fewer cafeterias than the menu already on the phone when one image
 * fails (OCR or network) while the others succeed. Saving that would replace a complete menu with a
 * partial one, so a fresh result that is the same post but has fewer tables loses to the current one.
 */
object MenuMerge {
    fun preferFuller(current: WeeklyMenu?, fresh: WeeklyMenu): WeeklyMenu =
        if (current != null && fresh.title == current.title && fresh.tables.size < current.tables.size) current else fresh
}
