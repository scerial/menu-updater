package com.menuupdater.weeklymenu.ocr

import android.graphics.BitmapFactory
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
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw MenuException(MenuError.NO_RECOGNIZED_TEXT)
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
