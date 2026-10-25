package com.menuupdater.weeklymenu.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.menuupdater.weeklymenu.data.MenuError
import com.menuupdater.weeklymenu.data.MenuException
import com.menuupdater.weeklymenu.data.Recognizer
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Korean OCR via ML Kit's bundled model. Converts each recognized line's pixel box into
 * the parser's bottom-left normalized space. The model is bundled in the APK, so it is always
 * available offline — hence [modelState] is always [ModelState.READY]; the other states exist only
 * so the UI can speak a single vocabulary if the model choice ever changes.
 */
class OcrService {
    enum class ModelState { READY, DOWNLOADING, UNAVAILABLE }

    val modelState: ModelState = ModelState.READY

    private val client = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    /**
     * Image touch-ups applied before OCR. [CONTRAST_UPSCALE] is what the app uses: measured on the
     * Note 20, it fixes several misreads the raw image loses (콩나물→공나물, 겉절이→걸절이, 삼계탕→삼계팅,
     * 후라이). The other two exist so the lexicon corpus dump can read each image three ways.
     */
    enum class Preprocess {
        NONE,
        CONTRAST,
        CONTRAST_UPSCALE;

        fun apply(source: Bitmap): Bitmap = when (this) {
            NONE -> source
            CONTRAST -> contrast(source, 1.6f)
            CONTRAST_UPSCALE -> scale(contrast(source, 1.6f), 1.5)
        }

        companion object {
            fun contrast(source: Bitmap, c: Float): Bitmap {
                val offset = (1f - c) / 2f * 255f
                val matrix = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, offset,
                        0f, c, 0f, 0f, offset,
                        0f, 0f, c, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                )
                val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                Canvas(out).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                return out
            }

            fun scale(source: Bitmap, factor: Double): Bitmap =
                Bitmap.createScaledBitmap(source, (source.width * factor).toInt(), (source.height * factor).toInt(), true)
        }
    }

    /** Usable directly as the [MenuRepository][com.menuupdater.weeklymenu.data.MenuRepository] recognizer. */
    val recognizer: Recognizer = { recognize(it) }

    suspend fun recognize(
        image: ByteArray,
        preprocess: Preprocess = Preprocess.CONTRAST_UPSCALE,
    ): List<RecognizedLine> = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw MenuException(MenuError.NO_RECOGNIZED_TEXT)
        // Boxes are normalized against the processed bitmap, so an upscale doesn't shift coordinates.
        val bitmap = preprocess.apply(source)
        val width = bitmap.width.toDouble()
        val height = bitmap.height.toDouble()
        val result = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)))
        result.textBlocks.asSequence()
            .flatMap { it.lines.asSequence() }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                RecognizedLine(
                    text = line.text,
                    box = Rect.fromTopDown(box.left / width, box.top / height, box.right / width, box.bottom / height),
                )
            }
            .toList()
    }
}
