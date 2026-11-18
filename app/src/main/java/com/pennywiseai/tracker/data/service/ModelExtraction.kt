package com.pennywiseai.tracker.data.service

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ExtractedTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The one-shot "read one transaction out of this text" turn for the on-device
 * model: the JSON schema that constrains the reply (the same constrained-
 * decoding recipe as the debug LLM probe and the chat-eval harness), the
 * prompts, and the parse. Nothing here touches chat history — the turn runs in
 * its own conversation through `LlmRepository.extractOneShot` and is closed
 * after, so Chat never sees it.
 */
object ModelExtraction {

    private val PROPERTIES = """
        "found":{"type":"boolean","description":"True only when the text states one spend or income with an amount"},
        "amount":{"type":"number","description":"Amount of money as a number, without currency symbol; 0 when not found"},
        "currency":{"type":"string","description":"ISO currency code if one is named, e.g. USD, or empty"},
        "type":{"type":"string","description":"EXPENSE for money spent or paid, INCOME for money received such as salary, refund or cashback"},
        "merchant":{"type":"string","description":"The shop, service or person the money went to or came from, or empty"},
        "category":{"type":"string","description":"One of the category names given, or empty"},
        "days_ago":{"type":"integer","description":"How many days ago: 0 for today or not said, 1 for yesterday"}
    """.trimIndent()

    /** Constrained decoding forces the reply into this shape (ResponseFormat.json). */
    val SCHEMA =
        """{"type":"object","properties":{$PROPERTIES},"required":["found","amount","currency","type","merchant","category","days_ago"]}"""

    /**
     * The turn's system prompt: today, the base currency and the category names
     * the reply must draw from, so a category the app doesn't have can't come back.
     */
    fun systemPrompt(
        baseCurrency: String,
        today: LocalDate,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
    ): String = """
        You read exactly one money transaction out of the text the user gives you, for an expense tracker.
        Today is $today. The user's default currency is $baseCurrency.
        Reply with JSON only: found is false when the text holds no spend or income with an amount; otherwise
        found true, the amount as a number, type EXPENSE or INCOME, the shop or person as merchant,
        one of the category names below as category, and days_ago (0 today, 1 yesterday, 2 the day before).
        Expense categories: ${expenseCategories.joinToString(", ")}
        Income categories: ${incomeCategories.joinToString(", ")}
    """.trimIndent()

    /** The text to read: the transcript or the image's words, as-is. */
    fun prompt(text: String): String = text

    @Serializable
    private data class Reply(
        val found: Boolean = false,
        val amount: Double = 0.0,
        val currency: String = "",
        val type: String = "",
        val merchant: String = "",
        val category: String = "",
        val days_ago: Int = 0,
    )

    // coerceInputValues: a null or wrong-typed field from the model takes the default.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * The model's reply as one entry, or null when it holds no transaction:
     * found false, an unreadable reply, or no positive amount (a model's JSON
     * is data, never trusted past this parse).
     */
    fun parse(reply: String): ExtractedTransaction? {
        // A fenced block or stray prose around the JSON: the schema constrains the
        // body, but a first reply on a fresh conversation can still wrap it.
        val body = reply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val r = runCatching { json.decodeFromString<Reply>(body) }.getOrNull() ?: return null
        if (!r.found) return null
        val amount = BigDecimal.valueOf(r.amount).abs().takeIf { it.signum() > 0 } ?: return null
        return ExtractedTransaction(
            amount = amount,
            currency = r.currency.trim().takeIf { it.length == 3 }?.uppercase(),
            type = when (r.type.trim().uppercase()) {
                "INCOME" -> TransactionType.INCOME
                "EXPENSE" -> TransactionType.EXPENSE
                else -> null
            },
            merchant = r.merchant.trim().takeIf { it.isNotEmpty() },
            category = r.category.trim().takeIf { it.isNotEmpty() },
            daysAgo = r.days_ago.coerceIn(0, 365),
        )
    }
}
