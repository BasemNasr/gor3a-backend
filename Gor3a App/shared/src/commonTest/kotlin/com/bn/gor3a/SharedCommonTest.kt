package com.bn.gor3a

import com.bn.gor3a.core.localization.formatNumberWithCommas
import com.bn.gor3a.core.localization.formatPrice
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.model.decodeImageBase64
import com.bn.gor3a.domain.model.encodeImageBase64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedCommonTest {

    @Test
    fun testPrescriptionImageBase64RoundTrip() {
        val sampleBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        val encoded = encodeImageBase64(sampleBytes)
        assertNotNull(encoded)

        val decoded = decodeImageBase64("data:image/jpeg;base64,$encoded")
        assertNotNull(decoded)
        assertContentEquals(sampleBytes, decoded)

        val rxFromCloud = SavedPrescription(
            id = "rx_test_1",
            title = "روشتة تجريبية",
            date = "25/10/2026",
            medicationsCount = 1,
            imageBase64 = encoded,
            prescriptionImageBytes = null
        )
        assertContentEquals(sampleBytes, rxFromCloud.resolveImageBytes())
    }


    @Test
    fun testFormatPriceWithCommas() {
        assertEquals("100,000", formatPrice(100000.0))
        assertEquals("143,750", formatPrice(143750.0))
        assertEquals("165,000", formatPrice(165000.0))
        assertEquals("134,201", formatPrice(134201.0))
        assertEquals("7,500", formatPrice(7500.0))
        assertEquals("23,958.33", formatPrice(23958.33))
        assertEquals("14,574.17", formatPrice(14574.17))
        assertEquals("13.5", formatPrice(13.5))
        assertEquals("6.75", formatPrice(6.75))
        assertEquals("22", formatPrice(22.0))
        assertEquals("1,234,567.89", formatPrice(1234567.89))
    }

    @Test
    fun testFormatNumberWithCommas() {
        assertEquals("24", formatNumberWithCommas(24))
        assertEquals("25,108", formatNumberWithCommas(25108))
        assertEquals("1,000,000", formatNumberWithCommas(1000000L))
    }
}