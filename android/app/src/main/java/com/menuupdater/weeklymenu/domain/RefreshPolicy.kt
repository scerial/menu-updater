package com.menuupdater.weeklymenu.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Decides whether cached menu data should be refreshed. Refresh when it is older than 30 minutes or
 * when the local date has changed since it was fetched (so "today" is re-evaluated on resume).
 */
object RefreshPolicy {
    val MAX_AGE: Duration = Duration.ofMinutes(30)

    fun shouldRefresh(
        lastFetchedEpochMs: Long,
        nowEpochMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (lastFetchedEpochMs <= 0L) return true
        val last = Instant.ofEpochMilli(lastFetchedEpochMs)
        val now = Instant.ofEpochMilli(nowEpochMs)
        if (Duration.between(last, now) >= MAX_AGE) return true
        return last.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()
    }
}
