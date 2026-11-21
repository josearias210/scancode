package com.scancode.app

import java.net.URI
import java.util.Locale

internal enum class CodeKind {
    TEXT, LINK, PHONE, EMAIL, MESSAGE, LOCATION, STORE, WIFI, CONTACT, EVENT, BARCODE,
}

internal data class ScanContent(
    val kind: CodeKind,
    val target: String? = null,
    val wifiNetwork: String? = null,
    val wifiPassword: String? = null,
) {
    companion object {
        private val wifiEntry = Regex("""(?:^|;)([A-Za-z]):((?:\\.|[^;\\])*)""")
        private val wifiEscape = Regex("""\\([\\;,:"])""")
        private val advertisedUrl = Regex("""^www\.""", RegexOption.IGNORE_CASE)
        private val networkSchemes = setOf("http", "https")
        private val actions = mapOf(
            "tel" to CodeKind.PHONE,
            "mailto" to CodeKind.EMAIL,
            "sms" to CodeKind.MESSAGE,
            "smsto" to CodeKind.MESSAGE,
            "geo" to CodeKind.LOCATION,
            "market" to CodeKind.STORE,
        )
        private val productFormats = setOf("EAN-13", "EAN-8", "UPC-A", "UPC-E", "ITF")

        fun parse(result: ScanResult): ScanContent {
            val value = result.value.trim()
            if (value.startsWith("WIFI:", ignoreCase = true)) {
                val fields = wifiEntry.findAll(value.drop(5)).associate { field ->
                    field.groupValues[1].uppercase(Locale.ROOT) to
                        field.groupValues[2].replace(wifiEscape) { it.groupValues[1] }
                }
                return ScanContent(
                    CodeKind.WIFI,
                    wifiNetwork = fields["S"]?.ifEmpty { null },
                    wifiPassword = fields["P"]?.ifEmpty { null },
                )
            }

            if (value.startsWith("BEGIN:VCARD", true) || value.startsWith("MECARD:", true)) {
                return ScanContent(CodeKind.CONTACT)
            }
            if (value.startsWith("BEGIN:VEVENT", true) || value.startsWith("BEGIN:VCALENDAR", true)) {
                return ScanContent(CodeKind.EVENT)
            }

            val candidate = if (advertisedUrl.containsMatchIn(value)) "https://$value" else value
            val uri = runCatching { URI(candidate) }.getOrNull()
            val scheme = uri?.scheme?.lowercase(Locale.ROOT)
            if (uri != null && scheme in networkSchemes && uri.host != null && uri.rawUserInfo == null) {
                return ScanContent(CodeKind.LINK, "$scheme:${uri.toASCIIString().substringAfter(':')}")
            }
            val kind = actions[scheme]
            if (uri != null && kind != null && !uri.rawSchemeSpecificPart.isNullOrBlank()) {
                return ScanContent(kind, "$scheme:${uri.rawSchemeSpecificPart}")
            }

            val textKind = if (result.format in productFormats) CodeKind.BARCODE else CodeKind.TEXT
            return ScanContent(textKind)
        }
    }
}
