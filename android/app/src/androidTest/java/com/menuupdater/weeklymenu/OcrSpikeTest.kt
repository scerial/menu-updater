package com.menuupdater.weeklymenu

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Step-2 OCR spike: runs ML Kit Korean on the real menu-image fixtures and dumps the
 * recognized lines with NORMALIZED bounding boxes to JSON. These JSON files become the
 * parser-test fixtures (replacing the iOS Vision fixtures). Pull them with:
 *   adb -s <serial> shell run-as com.menuupdater.weeklymenu ... (or from external files dir)
 */
@RunWith(AndroidJUnit4::class)
class OcrSpikeTest {

    private val images = listOf("hyangseol1.png", "faculty.png", "medical.png")

    @Test
    fun recognizeKoreanMenusAndDumpJson() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val testAssets = instr.context.assets                 // androidTest APK assets
        val outDir = instr.targetContext.getExternalFilesDir("ocr_spike")!!
        outDir.mkdirs()
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        for (name in images) {
            val bitmap = testAssets.open("menu_images/$name").use { BitmapFactory.decodeStream(it) }
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val image = InputImage.fromBitmap(bitmap, 0)

            val start = SystemClock.elapsedRealtime()
            val result: Text = Tasks.await(recognizer.process(image))
            val elapsed = SystemClock.elapsedRealtime() - start

            val lines = JSONArray()
            var lineCount = 0
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val b = line.boundingBox
                    val box = JSONArray()
                    if (b != null) {
                        box.put(b.left / w); box.put(b.top / h)
                        box.put(b.right / w); box.put(b.bottom / h)
                    }
                    lines.put(
                        JSONObject()
                            .put("text", line.text)
                            .put("box", box)                 // [left, top, right, bottom], normalized 0..1
                            .put("angle", line.angle)
                    )
                    lineCount++
                }
            }

            val root = JSONObject()
                .put("image", name)
                .put("width", bitmap.width)
                .put("height", bitmap.height)
                .put("elapsedMs", elapsed)
                .put("lineCount", lineCount)
                .put("fullText", result.text)
                .put("lines", lines)

            val outFile = File(outDir, name.removeSuffix(".png") + ".ocr.json")
            outFile.writeText(root.toString(2))
            val firstLine = result.text.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            Log.i("OcrSpike", "$name -> $lineCount lines in ${elapsed}ms, header=\"$firstLine\" (${outFile.absolutePath})")

            // Sanity: each weekly table should yield plenty of lines. (Exact text is assessed
            // from the dumped JSON, not asserted here, since OCR output varies between runs.)
            assertTrue("$name produced too few OCR lines ($lineCount)", lineCount > 20)
        }
    }
}
