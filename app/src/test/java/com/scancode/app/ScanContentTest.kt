package com.scancode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanContentTest {
    @Test
    fun classifiesWebLinksAndExpandsBareWww() {
        assertEquals(
            ScanContent(CodeKind.LINK, "https://www.example.com/tickets"),
            classify(" www.example.com/tickets "),
        )
        assertEquals(CodeKind.LINK, classify("https://example.com:8443/path?q=1").kind)
        assertEquals("https://example.com", classify("HTTPS://example.com").target)
    }

    @Test
    fun ambiguousOrUnsupportedUrisHaveNoDirectIntent() {
        listOf(
            "javascript:alert(1)", "intent://example.com/#Intent;end",
            "https:/example.com", "https://example.com@other.test", "https://bad host.test",
        ).forEach { input ->
            assertEquals(CodeKind.TEXT, classify(input).kind)
            assertNull(classify(input).target)
        }
    }

    @Test
    fun recognizesSupportedActionsAndKeepsEncodedParameters() {
        val inputs = mapOf(
            "tel:+18005551234" to CodeKind.PHONE,
            "mailto:hello@example.com?subject=Scan%20Code" to CodeKind.EMAIL,
            "smsto:5551234:hello" to CodeKind.MESSAGE,
            "geo:0,0?q=Caracas" to CodeKind.LOCATION,
            "market://details?id=com.example" to CodeKind.STORE,
        )
        for ((input, kind) in inputs) assertEquals(ScanContent(kind, input), classify(input))
    }

    @Test
    fun separatesWifiNetworkAndPasswordWithEscapedDelimiters() {
        assertEquals(
            ScanContent(CodeKind.WIFI, wifiNetwork = "Guest; 2", wifiPassword = """pa;ss\word"""),
            classify("""WIFI:T:WPA;S:Guest\; 2;P:pa\;ss\\word;;"""),
        )
        assertNull(classify("WIFI:T:nopass;S:Public;;").wifiPassword)
    }

    @Test
    fun detectsStructuredTextAndRetailBarcodes() {
        assertEquals(CodeKind.CONTACT, classify("BEGIN:VCARD\nFN:Alex\nEND:VCARD").kind)
        assertEquals(CodeKind.EVENT, classify("BEGIN:VEVENT\nSUMMARY:Meeting\nEND:VEVENT").kind)
        assertEquals(CodeKind.BARCODE, ScanContent.parse(ScanResult("012345678905", "UPC-A")).kind)
    }

    private fun classify(value: String) = ScanContent.parse(ScanResult(value, "QR"))
}
