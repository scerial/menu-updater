package com.menuupdater.weeklymenu.domain

/**
 * The in-app text-size tiers. Stored as the stable raw values -1/0/1 (so saved settings survive);
 * an unknown stored value falls back to [MEDIUM] (보통). The overall scale is fixed elsewhere; these
 * tiers only nudge it up or down.
 */
enum class TextSize(val raw: Int, val label: String) {
    SMALL(-1, "작게"),
    MEDIUM(0, "보통"),
    LARGE(1, "크게");

    companion object {
        fun fromRaw(raw: Int?): TextSize = entries.firstOrNull { it.raw == raw } ?: MEDIUM
    }
}
