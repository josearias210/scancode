package com.scancode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryJsonTest {
    @Test
    fun readsVersionOneHistoryAndSkipsBrokenRows() {
        val saved = """
            [{"value":"https://example.com","format":"QR","time":1730419200000},
             {"value":"incomplete"},"broken",{"value":"1234","format":"Código","time":50}]
        """.trimIndent()
        assertEquals(
            listOf(ScanResult("https://example.com", "QR", 1730419200000), ScanResult("1234", "Código", 50)),
            HistoryJson.decode(saved),
        )
    }

    @Test
    fun unreadableHistoryOpensAsEmpty() {
        assertTrue(HistoryJson.decode(null).isEmpty())
        assertTrue(HistoryJson.decode("broken json").isEmpty())
    }

    @Test
    fun roundTripsPayloadsAndCapsHistoryOrderedNewestFirst() {
        val saved = (201 downTo 1).map { ScanResult("Value $it\n—", "QR", it.toLong()) }
        assertEquals(saved.take(200), HistoryJson.decode(HistoryJson.encode(saved)))
    }
}
