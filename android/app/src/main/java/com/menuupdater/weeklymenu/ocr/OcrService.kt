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

    /** Usable directly as the [MenuRepository][com.menuupdater.weeklymenu.data.MenuRepository] recognizer. */
    val recognizer: Recognizer = ::recognize

    suspend fun recognize(image: ByteArray): List<RecognizedLine> = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw MenuException(MenuError.NO_RECOGNIZED_TEXT)
        // Measured on the Note 20: contrast + a 1.5x upscale fixes several misreads the raw image
        // loses (콩나물→공나물, 겉절이→걸절이, 삼계탕→삼계팅, 후라이) with little cost. Boxes are normalized
        // against this bitmap, so the upscale doesn't shift coordinates.
        val bitmap = preprocess(source)
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

    /** Boost contrast, then upscale 1.5x — the best-measured preprocessing for this menu artwork. */
    private fun preprocess(source: Bitmap): Bitmap {
        val c = 1.6f
        val offset = (1f - c) / 2f * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, offset,
                0f, c, 0f, 0f, offset,
                0f, 0f, c, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val contrasted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(contrasted).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return Bitmap.createScaledBitmap(contrasted, (source.width * 1.5).toInt(), (source.height * 1.5).toInt(), true)
    }
}
