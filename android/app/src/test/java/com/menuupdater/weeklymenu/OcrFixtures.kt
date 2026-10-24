package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads the ML Kit OCR fixtures captured on the Note 20 Ultra (step 2) into [RecognizedLine]s.
 * Boxes are `[left, top, right, bottom]` in top-left normalized space and are converted to the
 * parser's bottom-left space here.
 */
object OcrFixtures {
    @Serializable
    private data class OcrFile(val image: String, val lines: List<OcrLine>)

    @Serializable
    private data class OcrLine(val text: String, val box: List<Double>)

    private val json = Json { ignoreUnknownKeys = true }

    fun lines(name: String): List<RecognizedLine> {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/ocr/$name.ocr.json")
            ?: error("Missing OCR fixture: $name")
        val file = json.decodeFromString(OcrFile.serializer(), stream.bufferedReader().readText())
        return file.lines.map { line ->
            val (left, top, right, bottom) = line.box
            RecognizedLine(line.text, Rect.fromTopDown(left, top, right, bottom))
        }
    }

    fun scheduleJson(): String =
        javaClass.classLoader!!.getResourceAsStream("schedule.json")!!.bufferedReader().readText()

    /** Board/detail HTML fixtures under `resources/fixtures/`. */
    fun html(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name.html")!!.bufferedReader().readText()
}
