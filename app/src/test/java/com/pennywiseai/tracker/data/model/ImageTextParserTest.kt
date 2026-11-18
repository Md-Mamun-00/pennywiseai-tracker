package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** A receipt's or payment screenshot's words, read by rules before the model. */
class ImageTextParserTest {
    private val today = LocalDate.of(2026, 3, 20)

    private val receipt = """
        Sunrise Cafe
        Invoice
        12/03/2026
        Coffee        Rs.120
        Sandwich      Rs.180
        Grand Total   Rs.300
    """.trimIndent()

    @Test
    fun `the labelled total beats the items around it`() {
        val e = ImageTextParser.parse(receipt, today) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("300")))
        assertEquals("INR", e.currency)
        assertEquals("Sunrise Cafe", e.merchant)
        assertEquals(8, e.daysAgo)
        // Nothing says who was paid, so the form's own type stands.
        assertNull(e.type)
    }

    @Test
    fun `a subtotal is not the total`() {
        val e = ImageTextParser.parse("Shop\nSubtotal Rs.500\nTotal Rs.550", today) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("550")))
    }

    @Test
    fun `a paid line reads as the expense and the shop after To as the merchant`() {
        val e = ImageTextParser.parse(
            "Payment Successful\nPaid Rs.450\nTo Sharma Kirana\n12-03-2026 14:02",
            today,
        ) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("450")))
        assertEquals(TransactionType.EXPENSE, e.type)
        assertEquals("Sharma Kirana", e.merchant)
        assertEquals(8, e.daysAgo)
    }

    @Test
    fun `money coming in reads as income`() {
        val e = ImageTextParser.parse("Received Rs.5,000 on 05/03/2026", today) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("5000")))
        assertEquals(TransactionType.INCOME, e.type)
        assertEquals(15, e.daysAgo)
    }

    @Test
    fun `a bare figure on a short line is still an amount`() {
        val e = ImageTextParser.parse("Corner Mart\nTotal 450", today) ?: throw AssertionError("not parsed")
        assertEquals(0, e.amount!!.compareTo(BigDecimal("450")))
        assertNull(e.currency) // the form's own currency fills in
        assertEquals("Corner Mart", e.merchant)
    }

    @Test
    fun `text with no amount is nothing to import`() {
        assertNull(ImageTextParser.parse("Thank you for visiting\nCome again soon", today))
        assertNull(ImageTextParser.parse("", today))
    }

    @Test
    fun `an impossible or absent date leaves the day to the form`() {
        assertNull(ImageTextParser.parse("Cafe\nTotal Rs.100 on 45/13/2025", today)?.daysAgo)
        assertNull(ImageTextParser.parse("Cafe\nTotal Rs.100", today)?.daysAgo)
    }

    @Test
    fun `a US-style date is read only when the month says so`() {
        assertEquals(85, ImageTextParser.parse("Store\nTotal Rs.10\n12/25/2025", today)?.daysAgo)
    }

    @Test
    fun `a future date means today, not a count backwards`() {
        assertEquals(0, ImageTextParser.parse("Store\nTotal Rs.10\n2027-05-01", today)?.daysAgo)
    }

    @Test
    fun `a long header line is never the merchant`() {
        val e = ImageTextParser.parse(
            "Invoice No. 12345 dated 12/03/2026\nBlue Tokai Coffee\nTotal Rs.250",
            today,
        ) ?: throw AssertionError("not parsed")
        assertEquals("Blue Tokai Coffee", e.merchant)
    }
}
