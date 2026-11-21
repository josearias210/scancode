package com.scancode.app

import java.net.URI
import java.util.Locale

internal enum class LinkSignal { SHORTENER, IP_ADDRESS, INTERNATIONALIZED_HOST }

internal data class LinkReview(
    val authority: String,
    val usesHttps: Boolean,
    val signals: List<LinkSignal>,
) {
    companion object {
        private val shorteners = setOf(
            "bit.ly", "buff.ly", "cutt.ly", "goo.gl", "is.gd", "lnkd.in",
            "ow.ly", "rb.gy", "shorturl.at", "t.co", "tinyurl.com",
        )
        private val numericAddress = Regex("""\d+(?:\.\d+){0,3}""")

        fun inspect(target: String): LinkReview? {
            val uri = runCatching { URI(target) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if ((scheme != "http" && scheme != "https") || uri.rawUserInfo != null) return null

            val normalized = host.removeSuffix(".")
            val signals = buildList {
                if (normalized.removePrefix("www.") in shorteners) add(LinkSignal.SHORTENER)
                if (host.startsWith('[') || numericAddress.matches(normalized)) add(LinkSignal.IP_ADDRESS)
                if (host.split('.').any { it.startsWith("xn--") }) add(LinkSignal.INTERNATIONALIZED_HOST)
            }
            val authority = if (uri.port == -1) host else "$host:${uri.port}"
            return LinkReview(authority, scheme == "https", signals)
        }
    }
}
