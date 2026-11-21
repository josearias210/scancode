package com.scancode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkReviewTest {
    @Test
    fun showsTheAuthorityAndSchemeWithoutQueryData() {
        val review = requireNotNull(LinkReview.inspect("HTTPS://example.com:8443/tickets?code=123#notes"))
        assertEquals("example.com:8443", review.authority)
        assertTrue(review.usesHttps)
        assertTrue(review.signals.isEmpty())
    }

    @Test
    fun recognizesShortenerHostsAndUnencryptedLinks() {
        val shortener = requireNotNull(LinkReview.inspect("http://WWW.TINYURL.COM./sample"))
        assertFalse(shortener.usesHttps)
        assertEquals(listOf(LinkSignal.SHORTENER), shortener.signals)
        val unrelated = requireNotNull(LinkReview.inspect("https://tinyurl.com.example.org/sample"))
        assertTrue(unrelated.signals.isEmpty())
    }

    @Test
    fun keepsEncodedDomainAndIdentifiesLiteralIpDestinations() {
        val international = requireNotNull(LinkReview.inspect("https://xn--bcher-kva.example/"))
        assertEquals("xn--bcher-kva.example", international.authority)
        assertEquals(listOf(LinkSignal.INTERNATIONALIZED_HOST), international.signals)

        val ipv6 = requireNotNull(LinkReview.inspect("http://[2001:db8::1]:8080/"))
        assertEquals("[2001:db8::1]:8080", ipv6.authority)
        assertEquals(listOf(LinkSignal.IP_ADDRESS), ipv6.signals)
        assertEquals(listOf(LinkSignal.IP_ADDRESS), LinkReview.inspect("http://192.0.2.1")?.signals)
    }

    @Test
    fun excludesLinksThatCannotUseTheExistingOpenAction() {
        assertNull(LinkReview.inspect("https://person@example.org/"))
        assertNull(LinkReview.inspect("https:/example.org/"))
        assertNull(LinkReview.inspect("file:///example.org"))
    }
}
