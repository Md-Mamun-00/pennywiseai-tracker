package com.pennywiseai.tracker.data.service

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The model's one-shot reply, parsed into what the form fills in. */
class ModelExtractionTest {
    private fun reply(
        found: Boolean = true,
        amount: String = "450.0",
        currency: String = "INR",
        type: String = "EXPENSE",
        merchant: String = "Corner Cafe",
        category: String = "Food & Dining",
        daysAgo: Int = 0,
    ) = """{"found":$found,"amount":$amount,"currency":"$currency","type":"$type","merchant":"$merchant","category":"$category","days_ago":$daysAgo}"""

    @Test
    fun `a full reply becomes an entry`() {
        val e = ModelExtraction.parse(reply()) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("450.0")))
        assertEquals("INR", e.currency)
        assertEquals(TransactionType.EXPENSE, e.type)
        assertEquals("Corner Cafe", e.merchant)
        assertEquals("Food & Dining", e.category)
        assertEquals(0, e.daysAgo)
    }

    @Test
    fun `found false is no transaction, not a zero one`() {
        assertNull(ModelExtraction.parse(reply(found = false, amount = "0.0")))
    }

    @Test
    fun `a missing or non-positive amount is no transaction`() {
        assertNull(ModelExtraction.parse("""{"found":true,"amount":0.0,"currency":"INR","type":"EXPENSE","merchant":"x","category":"","days_ago":0}"""))
        assertNull(ModelExtraction.parse("""{"found":true,"amount":null,"currency":"INR","type":"EXPENSE","merchant":"x","category":"","days_ago":0}"""))
        // A minus sign is the model's wording, not a refund: the type carries the direction.
        val negative = ModelExtraction.parse("""{"found":true,"amount":-12.5,"currency":"INR","type":"EXPENSE","merchant":"x","category":"","days_ago":0}""")!!
        assertEquals(0, negative.amount!!.compareTo(BigDecimal("12.5")))
    }

    @Test
    fun `an unreadable reply is no transaction, never a crash`() {
        assertNull(ModelExtraction.parse(""))
        assertNull(ModelExtraction.parse("I could not find a transaction in that text."))
        assertNull(ModelExtraction.parse("{found: true}"))
    }

    @Test
    fun `a fenced reply is still read`() {
        val e = ModelExtraction.parse("```json\n${reply()}\n```") ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("450.0")))
    }

    @Test
    fun `an unknown type or short currency is dropped, not invented`() {
        val e = ModelExtraction.parse(reply(type = "TRANSFER", currency = "R")) ?: throw AssertionError("not parsed")
        assertNull(e.type)
        assertNull(e.currency)
    }

    @Test
    fun `an unknown day count is clamped into range`() {
        assertEquals(0, ModelExtraction.parse(reply(daysAgo = -3))!!.daysAgo)
        assertEquals(365, ModelExtraction.parse(reply(daysAgo = 99999))!!.daysAgo)
    }

    @Test
    fun `extra fields the model may add are ignored`() {
        val e = ModelExtraction.parse(
            """{"found":true,"amount":99.0,"currency":"USD","type":"INCOME","merchant":"Refund","category":"Income","days_ago":2,"account":"XX1234","confidence":0.9}"""
        ) ?: throw AssertionError("not parsed")
        assertEquals(TransactionType.INCOME, e.type)
        assertEquals(2, e.daysAgo)
    }

    @Test
    fun `the schema constrains every field the parse reads`() {
        listOf("found", "amount", "currency", "type", "merchant", "category", "days_ago").forEach {
            assertTrue("schema misses $it", "\"$it\":" in ModelExtraction.SCHEMA)
        }
        assertTrue(ModelExtraction.SCHEMA.startsWith("""{"type":"object"""))
    }

    @Test
    fun `toVoiceEntry maps onto the sheet, dropping what the lists don't know`() {
        val categories = listOf(
            CategoryEntity(id = 1, name = "Food & Dining", color = "#000"),
            CategoryEntity(id = 2, name = "Salary", color = "#000", isIncome = true),
        )
        val today = LocalDate.of(2026, 3, 10)
        val e = ModelExtraction.parse(reply(daysAgo = 1)) ?: throw AssertionError("not parsed")

        val voice = e.toVoiceEntry(categories, "INR", today) ?: throw AssertionError("no entry")
        assertEquals(0, voice.amount.compareTo(BigDecimal("450.0")))
        assertEquals("INR", voice.currency)
        assertEquals("Food & Dining", voice.category)
        assertEquals(today.minusDays(1), voice.date)

        // A category the model invented never reaches the sheet.
        val invented = e.copy(category = "Rocket Fuel").toVoiceEntry(categories, "INR", today)!!
        assertNull(invented.category)

        // No amount, no entry — the user types it.
        assertNull(e.copy(amount = null).toVoiceEntry(categories, "INR", today))
    }

    @Test
    fun `the prompts name today, the base currency and the real categories`() {
        val prompt = ModelExtraction.systemPrompt(
            baseCurrency = "INR",
            today = LocalDate.of(2026, 3, 10),
            expenseCategories = listOf("Food & Dining", "Groceries"),
            incomeCategories = listOf("Salary"),
        )
        assertTrue("2026-03-10" in prompt)
        assertTrue("INR" in prompt)
        assertTrue("Food & Dining, Groceries" in prompt)
        assertTrue("Salary" in prompt)
    }
}
