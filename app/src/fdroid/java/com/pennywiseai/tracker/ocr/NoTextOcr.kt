package com.pennywiseai.tracker.ocr

import android.net.Uri
import com.pennywiseai.tracker.domain.service.TextOcr
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The F-Droid flavor's OCR: nothing. ML Kit's recognizer ships Play services
 * AARs and ToS-licensed model code that this flavor must not carry, so image
 * import is unavailable there and the button stays hidden ([isAvailable]).
 */
@Singleton
class NoTextOcr @Inject constructor() : TextOcr {

    override val isAvailable: Boolean = false

    override suspend fun recognize(uri: Uri): String? = null
}
