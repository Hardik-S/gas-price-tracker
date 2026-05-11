package com.gasprice.domain

import com.gasprice.domain.model.ParsingStatus
import com.gasprice.domain.usecase.GasPriceParsing
import org.junit.Assert.*
import org.junit.Test

class GasPriceParsingTest {

    private fun parse(input: String) = GasPriceParsing.parse(input)

    // --- Direct numeric ---

    @Test
    fun `parse direct numeric cents-per-litre`() {
        val result = parse("167.9")
        assertEquals(167.9, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse pump price dollars-per-litre normalizes to cents`() {
        val result = parse("1.679")
        assertEquals(167.9, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse US gas price dollars per gallon`() {
        val result = parse("3.49")
        assertEquals(3.49, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse integer price`() {
        val result = parse("149")
        assertEquals(149.0, result.value!!, 0.001)
    }

    // --- Spoken word formats ---

    @Test
    fun `parse one forty nine point nine`() {
        val result = parse("one forty nine point nine")
        assertEquals(149.9, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse one sixty seven`() {
        val result = parse("one sixty seven")
        assertEquals(167.0, result.value!!, 0.001)
    }

    @Test
    fun `parse one dollar forty nine point nine`() {
        val result = parse("one dollar forty nine point nine")
        assertEquals(149.9, result.value!!, 0.001)
    }

    @Test
    fun `parse one forty nine`() {
        val result = parse("one forty nine")
        assertEquals(149.0, result.value!!, 0.001)
    }

    @Test
    fun `parse one sixty seven point nine`() {
        val result = parse("one sixty seven point nine")
        assertEquals(167.9, result.value!!, 0.001)
    }

    @Test
    fun `parse spoken pump price keeps decimal digits`() {
        val result = parse("one point six seven nine")
        assertEquals(167.9, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse spoken decimal preserves leading zero`() {
        val result = parse("one point zero nine")
        assertEquals(109.0, result.value!!, 0.001)
        assertEquals(ParsingStatus.SUCCESS, result.status)
    }

    @Test
    fun `parse two hundred`() {
        val result = parse("two hundred")
        assertEquals(200.0, result.value!!, 0.001)
    }

    // --- Edge cases ---

    @Test
    fun `blank input returns FAILED`() {
        val result = parse("")
        assertNull(result.value)
        assertEquals(ParsingStatus.FAILED, result.status)
    }

    @Test
    fun `gibberish returns FAILED`() {
        val result = parse("blahblah xyz")
        assertNull(result.value)
        assertEquals(ParsingStatus.FAILED, result.status)
    }

    @Test
    fun `implausibly high price returns FAILED`() {
        val result = parse("9999")
        assertNull(result.value)
        assertEquals(ParsingStatus.FAILED, result.status)
    }

    @Test
    fun `negative price returns FAILED`() {
        val result = parse("-5")
        assertNull(result.value)
        assertEquals(ParsingStatus.FAILED, result.status)
    }

    @Test
    fun `currency symbol stripped`() {
        val result = parse("$3.49")
        assertEquals(3.49, result.value!!, 0.001)
    }
}
