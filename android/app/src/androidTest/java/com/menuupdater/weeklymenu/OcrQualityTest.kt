package com.menuupdater.weeklymenu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Measures whether image preprocessing improves ML Kit Korean OCR on the menu images, scored
 * against a small set of dishes that the baseline currently misreads (콩나물→공나물, 겉절이→걸절이,
 * 삼계탕→삼계팅, 숭늉→숭능, 후라이→후리라이). Runs the bundled model on each variant and dumps a
 * report to the device's external files dir. This informs the model/preprocessing decision — it is
 * a measurement, not an assertion.
 */
@RunWith(AndroidJUnit4::class)
class OcrQualityTest {
    private val images = listOf("hyangseol1.png", "faculty.png", "medical.png")

    /** Correct spellings the baseline is known to miss; used as a directional accuracy proxy. */
    private val targets = listOf(
        "콩나물불고기", "콩나물무침", "콩나물국", "삼계탕", "겉절이", "숭늉", "후라이", "열무된장무침",
    )

    private val variants: List<Pair<String, (Bitmap) -> Bitmap>> = listOf(
        "baseline" to { it },
        "upscale2x" to { scale(it, 2.0) },
        "gray+contrast" to { contrast(grayscale(it), 1.6f) },
        "up2x+contrast" to { scale(contrast(it, 1.6f), 2.0) },
        "up2x+gray+contr" to { scale(contrast(grayscale(it), 1.6f), 2.0) },
        "up1_5x+contrast" to { scale(contrast(it, 1.6f), 1.5) },
        "contrast-only" to { contrast(it, 1.6f) },
    )

    @Test
    fun measurePreprocessingVariants() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val assets = instr.context.assets
        val outDir = instr.targetContext.getExternalFilesDir("ocr_quality")!!.apply { mkdirs() }
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        val report = StringBuilder()
        val found = linkedMapOf<String, MutableSet<String>>() // target -> variants that found it

        for ((variantName, transform) in variants) {
            var totalLines = 0
            var elapsed = 0L
            val combinedText = StringBuilder()
            for (name in images) {
                val src = assets.open("menu_images/$name").use { BitmapFactory.decodeStream(it) }
                val bitmap = transform(src)
                val start = SystemClock.elapsedRealtime()
                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                elapsed += SystemClock.elapsedRealtime() - start
                totalLines += result.textBlocks.sumOf { it.lines.size }
                combinedText.append(result.text).append('\n')
            }
            val text = combinedText.toString()
            val hits = targets.filter { text.contains(it) }
            hits.forEach { found.getOrPut(it) { mutableSetOf() }.add(variantName) }
            report.appendLine("%-14s targets=%d/%d  lines=%d  ocrMs=%d".format(variantName, hits.size, targets.size, totalLines, elapsed))
        }

        report.appendLine("\n--- per target (variants that read it correctly) ---")
        for (target in targets) {
            report.appendLine("%-10s %s".format(target, found[target]?.sorted()?.joinToString(", ") ?: "(none)"))
        }

        val outFile = File(outDir, "ocr-quality-report.txt")
        outFile.writeText(report.toString())
        Log.i("OcrQuality", "\n$report\n(${outFile.absolutePath})")
    }

    // --- preprocessing ---

    private fun grayscale(src: Bitmap): Bitmap = applyMatrix(src, ColorMatrix().apply { setSaturation(0f) })

    private fun contrast(src: Bitmap, c: Float): Bitmap {
        val t = (1f - c) / 2f * 255f
        val m = ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
        return applyMatrix(src, m)
    }

    private fun applyMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    private fun scale(src: Bitmap, factor: Double): Bitmap =
        Bitmap.createScaledBitmap(src, (src.width * factor).toInt(), (src.height * factor).toInt(), true)

    private fun binarize(src: Bitmap, threshold: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            val v = if (lum >= threshold) 0xFF else 0x00
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
