package com.menuupdater.weeklymenu.data

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.MenuSource
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.MenuTableParser
import com.menuupdater.weeklymenu.domain.TableVoter
import com.menuupdater.weeklymenu.domain.RecognizedLine
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Recognizes the text in a menu image. Supplied by the OCR layer (ML Kit) in step 5. */
typealias Recognizer = suspend (ByteArray) -> List<RecognizedLine>

/**
 * Loads the latest 순천향대학교 post and the matching 의과대학 post, and merges their tables. One post
 * failing doesn't discard the other; the load only fails when neither produces a menu. A port of
 * the iOS `MenuRepository`, with OCR injected as [recognizer] so this layer stays testable.
 */
class MenuRepository(
    private val recognizer: Recognizer,
    /**
     * Further readings of the same image (other preprocessing). Their tables vote with the main
     * reading, so a letter misread in one variant is outvoted by the others. Optional.
     */
    private val variantRecognizers: List<Recognizer> = emptyList(),
    private val client: OkHttpClient = defaultClient(),
    private val boardUrl: String = "https://www.agoodplacefnb.com/today",
    /** Fixes OCR misreads in dish names using past menus; null leaves the OCR text as read. */
    private val corrector: DishCorrector? = null,
) {
    suspend fun fetchLatestMenu(): WeeklyMenu {
        val (university, medical) = findLatestPosts()

        val tables = mutableListOf<MenuTable>()
        val sources = mutableListOf<MenuSource>()
        var firstError: Throwable? = null
        for (post in listOfNotNull(university, medical)) {
            try {
                tables += fetchTables(post)
                sources += MenuSource(post.title, post.date, post.url)
            } catch (e: Throwable) {
                if (firstError == null) firstError = e
            }
        }
        val primary = sources.firstOrNull()
            ?: throw (firstError ?: MenuException(MenuError.NO_RECOGNIZED_TEXT))

        return WeeklyMenu(
            title = primary.title,
            postedAt = primary.postedAt,
            sourceURL = primary.url,
            tables = tables,
            additionalSources = sources.drop(1),
        )
    }

    private suspend fun fetchTables(post: MenuPost): List<MenuTable> {
        val html = fetchString(post.url)
        val imageUrls = BoardParser.menuImageUrls(html, post.url)
        if (imageUrls.isEmpty()) throw MenuException(MenuError.NO_MENU_IMAGES)

        // Each image is a separate cafeteria's table. One image failing shouldn't discard the
        // others; only fail when no image produced a table.
        val tables = mutableListOf<MenuTable>()
        var firstError: Throwable? = null
        for (imageUrl in imageUrls) {
            try {
                val bytes = fetchBytes(imageUrl)
                val readings = listOfNotNull(MenuTableParser.parse(recognizer(bytes))) +
                    variantRecognizers.mapNotNull { variant ->
                        // A failing extra reading only loses its vote.
                        try {
                            MenuTableParser.parse(variant(bytes))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                if (readings.isNotEmpty()) {
                    val voted = TableVoter.vote(readings, isKnown = { corrector?.knows(it) ?: false })
                    tables.add(corrector?.correct(voted) ?: voted)
                }
            } catch (e: Throwable) {
                if (firstError == null) firstError = e
            }
        }
        if (tables.isEmpty()) throw (firstError ?: MenuException(MenuError.NO_RECOGNIZED_TEXT))
        return tables
    }

    private suspend fun findLatestPosts(): Pair<MenuPost?, MenuPost?> {
        val universityPosts = mutableListOf<MenuPost>()
        val medicalPosts = mutableListOf<MenuPost>()
        var firstError: Throwable? = null
        for (page in 1..5) {
            val url = pageUrl(page)
            // A single failing page shouldn't discard posts found on other pages.
            try {
                val html = fetchString(url)
                universityPosts += BoardParser.eligiblePosts(html, url)
                medicalPosts += BoardParser.medicalPosts(html, url)
            } catch (e: Throwable) {
                if (firstError == null) firstError = e
            }
            // The board lists newest posts first, and both posts of a week go up together, so the
            // first page with a match holds the latest of each.
            if (universityPosts.isNotEmpty() || medicalPosts.isNotEmpty()) break
        }
        val university = universityPosts.maxByOrNull { it.date }
        val medical = medicalPosts.maxByOrNull { it.date }
        if (university == null && medical == null) {
            throw (firstError ?: MenuException(MenuError.NO_ELIGIBLE_POST))
        }
        return university to medical
    }

    private fun pageUrl(page: Int): String =
        if (page == 1) boardUrl
        else "$boardUrl?q=$PAGE_QUERY&page=$page"

    private suspend fun fetchString(url: String): String =
        fetchBytes(url).toString(Charsets.UTF_8)

    private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 WeeklyMenuApp")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body?.bytes() ?: throw IOException("Empty body for $url")
        }
    }

    companion object {
        // The board's "all keywords" search token; pages past the first carry it plus &page=N.
        // Internal so the instrumented corpus dump (DishCorpusDump) can walk the same pages.
        internal const val PAGE_QUERY = "YToxOntzOjEyOiJrZXl3b3JkX3R5cGUiO3M6MzoiYWxsIjt9"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
