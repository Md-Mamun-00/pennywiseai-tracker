package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.presentation.add.SharedTextAmount
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The transaction a receipt or payment screenshot states, read with rules
 * before the model runs: OCR words in, [ExtractedTransaction] out — or null
 * when no amount is clear, so the caller can try the model, or leave the form
 * for the user to fill in.
 *
 * The amount comes from the labelled line that matters most ("Grand Total"
 * beats an item's "Price"), falling back to the first line that names a
 * figure — receipts label their total, and messages state the sum first. The
 * type is only what the words say was done (spent / received / credited);
 * a receipt with no such word stays whatever the form already says.
 */
object ImageTextParser {

    // A receipt's payable figure, most decisive first.
    private val AMOUNT_LABELS = listOf(
        "grand total", "total", "amount due", "payable", "bill", "amount", "paid", "price",
    )
    // Label as a whole word, so "subtotal" is no "total" (but "billing" is a "bill").
    private val LABEL_WORDS = AMOUNT_LABELS.map { Regex("""\b${Regex.escape(it)}""", RegexOption.IGNORE_CASE) }

    // Header/status words a shop's name is never made of. The spend/receive verbs
    // too: "Received Rs.5,000" states a direction, it isn't the counterparty.
    private val NOISE = Regex(
        """(?i)\b(?:invoice|receipt|order|date|time|ref(?:erence)?|txn|transaction|status|""" +
            """successful|success|failed|payment|upi|paid|total|subtotal|amount|price|bill|""" +
            """received|credited|debited|deposited|withdrawn|refund(?:ed)?|cashback|""" +
            """balance|avail(?:able)?|change|thank you|visit|www|https?)\b"""
    )

    // Day first where both parts could be a month (most of the world writes it so);
    // only an impossible month says otherwise ("12/25/2026" is December).
    private val DATE = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b|\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")

    // "To Sharma Kirana" (a UPI screenshot) is the shop, not the preposition.
    private val LEADING_DIRECTION = Regex("""(?i)^(?:to|from|at|@)\s+""")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ExtractedTransaction? {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        if (lines.isEmpty()) return null
        val amount = amountOf(lines) ?: return null

        return ExtractedTransaction(
            amount = amount.amount,
            currency = amount.currency,
            type = TransactionDraft.statedType(text, amount.amount),
            merchant = merchantOf(lines),
            daysAgo = daysAgoOf(text, today),
        )
    }

    /** The labelled line's figure (label priority), else the first line that states one. */
    private fun amountOf(lines: List<String>): SharedTextAmount.Guess? =
        LABEL_WORDS.firstNotNullOfOrNull { label ->
            lines.firstNotNullOfOrNull { line -> SharedTextAmount.extract(line)?.takeIf { label.containsMatchIn(line) } }
        } ?: lines.firstNotNullOfOrNull(SharedTextAmount::extract)

    /** The first line that reads like a name: letters, no header words, not mostly digits. */
    private fun merchantOf(lines: List<String>): String? = lines.firstOrNull { line ->
        line.length in 2..40 &&
            line.count { it.isLetter() } >= 2 &&
            line.count { it.isDigit() } * 2 <= line.length &&
            !NOISE.containsMatchIn(line)
    }?.replace(LEADING_DIRECTION, "")?.takeIf { it.length >= 2 }

    /** How many days before [today] the first date in the text was, clamped to a year. */
    private fun daysAgoOf(text: String, today: LocalDate): Int? {
        val m = DATE.find(text) ?: return null
        val (year, month, day) = if (m.groupValues[1].isNotEmpty()) {
            Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } else {
            val rawFirst = m.groupValues[4].toInt()
            val rawSecond = m.groupValues[5].toInt()
            val d = if (rawFirst > 12 || rawSecond <= 12) rawFirst else rawSecond
            val mo = if (rawFirst > 12 || rawSecond <= 12) rawSecond else rawFirst
            val y = m.groupValues[6].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            Triple(y, mo, d)
        }
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        // A future or absurd date says nothing; the form's own date stands (0 = today).
        return ChronoUnit.DAYS.between(date, today).coerceIn(0, 365).toInt()
    }
}
