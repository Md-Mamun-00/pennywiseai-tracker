package com.pennywiseai.tracker.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pennywiseai.tracker.domain.service.TextOcr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR for the image import (standard flavor): ML Kit's bundled Latin
 * recognizer, no network and no Play-services model download. The recognizer
 * is a process-wide shared client, so one is held for the app's life.
 */
@Singleton
class MlKitTextOcr @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextOcr {

    override val isAvailable: Boolean = true

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image).await().text.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // An unreadable file or an image too large to decode: nothing to import.
            null
        }
    }
}
