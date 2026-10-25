package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.data.MenuRepository
import com.menuupdater.weeklymenu.data.Recognizer
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Orchestration and failure-isolation tests. Uses MockWebServer with synthetic HTML (the big
 * fixtures cover real-DOM parsing in [BoardParserTest]) and an injected recognizer, so no network
 * or OCR is involved. Image URLs route back to the mock by embedding `cdn.imweb.me/upload` in the
 * mock path, which satisfies [com.menuupdater.weeklymenu.data.BoardParser]'s host filter.
 */
class MenuRepositoryTest {
    private lateinit var server: MockWebServer

    /** Minimal OCR output that [MenuTableParser] turns into a one-day table. */
    private val recognizer: Recognizer = {
        listOf(
            RecognizedLine("10월20일(화)", Rect(0.30, 0.70, 0.10, 0.03)),
            RecognizedLine("김치", Rect(0.30, 0.50, 0.10, 0.03)),
        )
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String) = server.url(path).toString()

    private fun boardHtml(university: String?, medical: String?): String {
        fun row(title: String, href: String, time: String) =
            """<ul class="li_body"><li class="tit"><a class="list_text_title" href="$href">$title</a></li>""" +
                """<li class="time" title="$time"></li></ul>"""
        val rows = buildString {
            if (university != null) append(row("26년 10월 4주차 순천향대학교 식단", university, "2026-10-16 09:45"))
            if (medical != null) append(row("26년 10월 4주차 순천향대학교 의과대학 식단", medical, "2026-10-16 09:46"))
        }
        return "<html><body>$rows</body></html>"
    }

    private fun detailHtml(imagePath: String) =
        """<html><body><img src="${url(imagePath)}"></body></html>"""

    @Test
    fun mergesUniversityAndMedicalPosts() {
        server.dispatcher = dispatcher()
        val menu = runBlocking { repository().fetchLatestMenu() }

        assertEquals(2, menu.tables.size)
        assertEquals("26년 10월 4주차 순천향대학교 식단", menu.title)
        assertEquals(1, menu.additionalSources!!.size)
        assertEquals(2, menu.sources.size)
    }

    @Test
    fun oneFailingPostDoesNotDiscardTheOther() {
        server.dispatcher = dispatcher(medicalDetailCode = 500)
        val menu = runBlocking { repository().fetchLatestMenu() }

        assertEquals(1, menu.tables.size)
        assertEquals("26년 10월 4주차 순천향대학교 식단", menu.title)
        assertTrue(menu.additionalSources!!.isEmpty())
    }

    @Test
    fun throwsWhenNeitherPostYieldsAMenu() {
        server.dispatcher = dispatcher(universityDetailCode = 500, medicalDetailCode = 500)
        assertThrows(Exception::class.java) {
            runBlocking { repository().fetchLatestMenu() }
        }
    }

    @Test
    fun fallsThroughToLaterPagesWhenTheFirstHasNoMatches() {
        server.dispatcher = dispatcher(firstPageHasMatches = false)
        val menu = runBlocking { repository().fetchLatestMenu() }

        assertEquals(2, menu.tables.size)
    }

    @Test
    fun otherReadingsOutvoteAMisreadInTheMainOne() {
        server.dispatcher = dispatcher()
        val repository = MenuRepository(
            recognizer = readingOf("공나물국"),
            variantRecognizers = listOf(readingOf("콩나물국"), readingOf("콩나물국")),
            boardUrl = url("/today"),
        )
        val menu = runBlocking { repository.fetchLatestMenu() }

        val items = menu.tables.first().days.single().meals.single().items
        assertEquals(listOf("콩나물국"), items)
    }

    @Test
    fun aFailingExtraReadingOnlyLosesItsVote() {
        server.dispatcher = dispatcher()
        val repository = MenuRepository(
            recognizer = readingOf("콩나물국"),
            variantRecognizers = listOf({ error("ocr crashed") }, readingOf("콩나물국")),
            boardUrl = url("/today"),
        )
        val menu = runBlocking { repository.fetchLatestMenu() }

        assertEquals(2, menu.tables.size)
        assertEquals(listOf("콩나물국"), menu.tables.first().days.single().meals.single().items)
    }

    /** A one-day, one-dish reading, like [recognizer] but with the given dish. */
    private fun readingOf(dish: String): Recognizer = {
        listOf(
            RecognizedLine("10월20일(화)", Rect(0.30, 0.70, 0.10, 0.03)),
            RecognizedLine(dish, Rect(0.30, 0.50, 0.10, 0.03)),
        )
    }

    private fun repository() = MenuRepository(
        recognizer = recognizer,
        boardUrl = url("/today"),
    )

    private fun dispatcher(
        universityDetailCode: Int = 200,
        medicalDetailCode: Int = 200,
        firstPageHasMatches: Boolean = true,
    ) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.contains("/upload/") -> MockResponse().setBody("image-bytes")
                path.contains("/post_u") -> MockResponse().setResponseCode(universityDetailCode)
                    .setBody(detailHtml("/cdn.imweb.me/upload/u.png"))
                path.contains("/post_m") -> MockResponse().setResponseCode(medicalDetailCode)
                    .setBody(detailHtml("/cdn.imweb.me/upload/m.png"))
                path.startsWith("/today") -> {
                    val matches = firstPageHasMatches || path.contains("page=")
                    MockResponse().setBody(
                        if (matches) boardHtml(url("/post_u"), url("/post_m"))
                        else "<html><body><ul class=\"li_body\"><li class=\"tit\">" +
                            "<a class=\"list_text_title\" href=\"/x\">공지사항</a></li>" +
                            "<li class=\"time\" title=\"2026-10-16 09:00\"></li></ul></body></html>",
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }
}
