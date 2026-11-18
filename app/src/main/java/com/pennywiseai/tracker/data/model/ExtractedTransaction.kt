package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One transaction read out of free text — a voice transcript, a screenshot's
 * words — by the on-device model ([ModelExtraction]) or the image rules
 * ([ImageTextParser]). Every field is optional: only what the text actually
 * settles is filled in, and the user checks the rest in the form.
 */
data class ExtractedTransaction(
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val type: TransactionType? = null,
    val merchant: String? = null,
    val category: String? = null,
    /** Days before today: 0 today, 1 yesterday. Null when no day was said. */
    val daysAgo: Int? = null,
) {
    /**
     * This as the quick-add sheet's [VoiceEntry], or null when there's no amount
     * to fill. A category the lists don't know is dropped (the user picks), an
     * unknown currency falls back to [baseCurrency], and no day said means today.
     */
    fun toVoiceEntry(
        categories: List<CategoryEntity>,
        baseCurrency: String,
        today: LocalDate = LocalDate.now(),
    ): VoiceEntry? {
        val amount = amount?.abs()?.takeIf { it.signum() > 0 } ?: return null
        val type = type ?: TransactionType.EXPENSE
        return VoiceEntry(
            amount = amount,
            currency = currency?.takeIf { it.length == 3 } ?: baseCurrency,
            type = type,
            merchant = merchant?.trim().orEmpty(),
            category = category?.let { matchCategory(categories, it, type) },
            date = today.minusDays((daysAgo ?: 0).coerceIn(0, 365).toLong()),
        )
    }

    companion object {
        /**
         * [wanted] as one of [categories]' names, tolerant of case, "&"/"and" and
         * punctuation ("Food" → "Food & Dining"), and only among [type]'s own
         * categories; null when none matches — the model must not invent one.
         */
        fun matchCategory(categories: List<CategoryEntity>, wanted: String, type: TransactionType): String? {
            val ofType = categories.filter { it.isIncome == (type == TransactionType.INCOME) && !it.isHidden }
            val norm = { x: String ->
                x.lowercase().replace("&", "and").replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()
            }
            val w = norm(wanted)
            if (w.isEmpty()) return null
            return ofType.firstOrNull { norm(it.name) == w }?.name
                ?: ofType.firstOrNull { norm(it.name).startsWith(w) || w.startsWith(norm(it.name)) }?.name
                ?: ofType.firstOrNull { norm(it.name).split(" ").first() == w.split(" ").first() }?.name
        }
    }
}
