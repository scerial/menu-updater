package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.data.BoardParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board scraping against the real HTML fixtures captured in step 2. */
class BoardParserTest {
    private val baseUrl = "https://www.agoodplacefnb.com/today"

    @Test
    fun selectsMainCampusPostsAndPicksTheNewest() {
        val posts = BoardParser.eligiblePosts(OcrFixtures.html("board_page1"), baseUrl)

        assertEquals(5, posts.size)
        assertTrue(posts.none { it.title.contains("의과대학") })
        assertTrue(posts.all { it.title.contains("순천향대학교") })
        assertTrue(posts.all { it.url.startsWith("https://www.agoodplacefnb.com/today") })

        val newest = posts.maxByOrNull { it.date }!!
        assertEquals("2026-10-16", newest.date)
        assertTrue(newest.url.contains("idx=175120835"))
        assertEquals("26년 10월 4주차 순천향대학교 식단", newest.title)
    }

    @Test
    fun selectsMedicalSchoolPostsAndPicksTheNewest() {
        val posts = BoardParser.medicalPosts(OcrFixtures.html("board_page1"), baseUrl)

        assertEquals(5, posts.size)
        assertTrue(posts.all { it.title.contains("의과대학") })

        val newest = posts.maxByOrNull { it.date }!!
        assertEquals("2026-10-16", newest.date)
        assertTrue(newest.url.contains("idx=175120850"))
    }

    @Test
    fun extractsMenuImagesExcludingLogoAndFavicon() {
        val university = BoardParser.menuImageUrls(OcrFixtures.html("detail_university_175120835"), baseUrl)
        assertEquals(2, university.size)
        assertTrue(university.all { it.contains("cdn.imweb.me/upload") })
        assertTrue(university.none { it.contains("20a4b9b3cebc9") || it.endsWith(".ico") })

        val medical = BoardParser.menuImageUrls(OcrFixtures.html("detail_medical_175120850"), baseUrl)
        assertEquals(1, medical.size)
    }
}
