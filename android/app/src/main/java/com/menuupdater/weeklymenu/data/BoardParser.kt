package com.menuupdater.weeklymenu.data

import org.jsoup.Jsoup

/**
 * Pure HTML parsing of the Imweb board (`t=board`) and its detail posts. No networking, so it runs
 * directly against the committed fixtures. A port of the iOS `MenuRepository` static helpers, using
 * the board's actual DOM: rows `ul.li_body`, title `a.list_text_title`, date `li.time[title]`.
 */
object BoardParser {
    private val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Posts for the main campus cafeterias (순천향대학교, not 의과대학), newest first as listed. */
    fun eligiblePosts(html: String, baseUrl: String): List<MenuPost> =
        posts(html, baseUrl) { it.contains("순천향대학교") && !it.contains("의과대학") }

    /** Posts for the 의과대학 (천안의대) cafeteria. */
    fun medicalPosts(html: String, baseUrl: String): List<MenuPost> =
        posts(html, baseUrl) { it.contains("순천향대학교") && it.contains("의과대학") }

    private fun posts(html: String, baseUrl: String, isEligible: (String) -> Boolean): List<MenuPost> {
        val document = Jsoup.parse(html, baseUrl)
        val result = mutableListOf<MenuPost>()
        for (row in document.select("ul.li_body")) {
            val link = row.selectFirst("a.list_text_title") ?: continue
            val title = link.text().trim()
            if (!isEligible(title)) continue
            val url = link.absUrl("href")
            if (url.isEmpty()) continue
            // Posts without a parseable date can't be ordered, so they are skipped.
            val date = dateRegex.find(row.selectFirst("li.time")?.attr("title").orEmpty())?.value
                ?: continue
            result.add(MenuPost(title, date, url))
        }
        return result
    }

    /**
     * The menu image URLs on a detail page: `cdn.imweb.me/upload` images, excluding the site logo
     * (`20a4b9b3cebc9.png`) and the favicon (`.ico`).
     */
    fun menuImageUrls(html: String, baseUrl: String): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        val urls = linkedSetOf<String>()
        for (img in document.select("img")) {
            for (attr in IMAGE_ATTRS) {
                val url = img.absUrl(attr)
                if (url.contains("cdn.imweb.me/upload") &&
                    !url.contains("20a4b9b3cebc9") &&
                    !url.endsWith(".ico")
                ) {
                    urls.add(url)
                }
            }
        }
        return urls.toList()
    }

    // Imweb lazy-loads some images, so the real URL can live in a data-* attribute.
    private val IMAGE_ATTRS = listOf("src", "data-src", "data-original")
}
