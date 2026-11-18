package com.pennywiseai.tracker.domain.service

import android.net.Uri

/**
 * The words in an image — a receipt, a payment screenshot — read on-device.
 * The standard flavor binds ML Kit's bundled recognizer; the F-Droid flavor
 * (no Play services, no ToS-licensed model) binds an unavailable stub, and the
 * import button hides behind [isAvailable].
 */
interface TextOcr {
    /** False when this build ships no recognizer. */
    val isAvailable: Boolean

    /** The image's words, or null when it can't be read or holds no text. */
    suspend fun recognize(uri: Uri): String?
}
