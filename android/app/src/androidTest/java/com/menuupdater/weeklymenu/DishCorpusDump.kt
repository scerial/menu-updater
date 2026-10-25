package com.menuupdater.weeklymenu

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.menuupdater.weeklymenu.data.BoardParser
import com.menuupdater.weeklymenu.data.MenuPost
import com.menuupdater.weeklymenu.data.MenuRepository
import com.menuupdater.weeklymenu.ocr.OcrService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Saves raw OCR output for every past menu image on the board, read with each preprocessing variant,
 * as the corpus the JVM `BuildDishLexicon` turns into `assets/dish-lexicon.json`. Not a regular test:
 * run it by hand (see ocr/OCR_PLAN.md), because `connectedAndroidTest` uninstalls the app and
 * deletes the output.
 *
 * Output: `files/corpus/<postDate>_<u|m>_<postId>_<imgN>_<variant>.ocr.json`, with boxes in the same
 * top-down normalized `[left, top, right, bottom]` form as the test fixtures.
 */
@RunWith(AndroidJUnit4::class)
class DishCorpusDump {
    private val boardUrl = "https://www.agoodplacefnb.com/today"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Test
    fun dumpCorpus() {
        runBlocking { dump() }
    }

    private suspend fun dump() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = context.getExternalFilesDir("corpus")!!
        outDir.listFiles()?.forEach { it.delete() }
        outDir.mkdirs()
        val ocr = OcrService()
        val start = SystemClock.elapsedRealtime()

        // Walk the board until a page adds no new posts (past the end it repeats the last page).
        val posts = linkedMapOf<String, Pair<MenuPost, String>>() // url -> (post, kind)
        for (page in 1..MAX_PAGES) {
            val url = if (page == 1) boardUrl else "$boardUrl?q=${MenuRepository.PAGE_QUERY}&page=$page"
            val html = try {
                fetch(url).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "page $page failed: $e"); continue
            }
            val found = BoardParser.eligiblePosts(html, url).map { it to "u" } +
                BoardParser.medicalPosts(html, url).map { it to "m" }
            val before = posts.size
            for ((post, kind) in found) posts.putIfAbsent(post.url, post to kind)
            Log.i(TAG, "page $page: ${found.size} posts, ${posts.size - before} new")
            if (posts.size == before) break
        }

        var images = 0
        var passes = 0
        var failures = 0
        var ocrMs = 0L
        for ((post, kind) in posts.values) {
            val postId = post.url.substringAfterLast("idx=", post.url.hashCode().toUInt().toString())
                .takeWhile { it.isLetterOrDigit() }
            val imageUrls = try {
                BoardParser.menuImageUrls(fetch(post.url).toString(Charsets.UTF_8), post.url)
            } catch (e: Exception) {
                Log.w(TAG, "post ${post.date} ${post.title} failed: $e"); failures++; continue
            }
            for ((n, imageUrl) in imageUrls.withIndex()) {
                val bytes = try {
                    fetch(imageUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "image $imageUrl failed: $e"); failures++; continue
                }
                images++
                for (variant in OcrService.Preprocess.entries) {
                    try {
                        val t = SystemClock.elapsedRealtime()
                        val lines = ocr.recognize(bytes, variant)
                        ocrMs += SystemClock.elapsedRealtime() - t
                        passes++
                        val json = JSONObject()
                            .put("image", imageUrl)
                            .put("variant", variant.name)
                            .put("postDate", post.date)
                            .put("postTitle", post.title)
                            .put("kind", kind)
                            .put("lines", JSONArray().apply {
                                for (line in lines) {
                                    // Back from the parser's bottom-left space to top-down, like the fixtures.
                                    val b = line.box
                                    val box = JSONArray()
                                    box.put(b.x)
                                    box.put(1.0 - (b.y + b.height))
                                    box.put(b.x + b.width)
                                    box.put(1.0 - b.y)
                                    put(JSONObject().put("text", line.text).put("box", box))
                                }
                            })
                        val name = "${post.date}_${kind}_${postId}_${n}_${variant.name}.ocr.json"
                        File(outDir, name).writeText(json.toString())
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR $variant of $imageUrl failed: $e"); failures++
                    }
                }
            }
            Log.i(TAG, "post ${post.date} $kind: ${imageUrls.size} images (total $images)")
        }

        val totalS = (SystemClock.elapsedRealtime() - start) / 1000
        val summary = "posts=${posts.size} images=$images ocrPasses=$passes failures=$failures " +
            "ocrMs=$ocrMs totalS=$totalS dates=${posts.values.minOfOrNull { it.first.date }}.." +
            "${posts.values.maxOfOrNull { it.first.date }}"
        File(outDir, "_summary.txt").writeText(summary)
        Log.i(TAG, "DONE $summary (${outDir.absolutePath})")
    }

    private fun fetch(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 WeeklyMenuApp").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.bytes() ?: throw IOException("Empty body for $url")
        }
    }

    private companion object {
        const val TAG = "DishCorpus"
        const val MAX_PAGES = 15
    }
}
