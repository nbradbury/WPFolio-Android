package com.nbradbury.wpfolio.data.wpcom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WpcomAuthTest {

    @Test
    fun `reads the code from the redirect query`() {
        assertEquals("abc+123", WpcomAuth.parseRedirect("code=abc%2B123&state=s1", expectedState = "s1"))
    }

    @Test
    fun `surfaces WordPress_com errors from the redirect`() {
        val error = runCatching {
            WpcomAuth.parseRedirect("error=access_denied&error_description=You+said+no&state=s1", "s1")
        }.exceptionOrNull()
        assertEquals("You said no", error?.message)
    }

    @Test
    fun `rejects a mismatched or missing state`() {
        listOf("other", null).forEach { expected ->
            val error = runCatching { WpcomAuth.parseRedirect("code=abc&state=s1", expected) }.exceptionOrNull()
            assertTrue(error is IOException)
        }
    }

    @Test
    fun `parses the token response`() {
        val token = WpcomAuth.parseTokenResponse(
            """{"access_token":"tok","token_type":"bearer","blog_id":"0","scope":"global","expires_in":1209600}""",
            successful = true,
            now = 1_000L,
        )
        assertEquals("tok", token.accessToken)
        assertEquals(1_000L + 1_209_600_000L, token.expiresAt)

        assertNull(WpcomAuth.parseTokenResponse("""{"access_token":"tok"}""", successful = true).expiresAt)
    }

    @Test
    fun `token errors carry WordPress_com's description`() {
        val error = runCatching {
            WpcomAuth.parseTokenResponse(
                """{"error":"invalid_grant","error_description":"Invalid authorization_code."}""",
                successful = false,
            )
        }.exceptionOrNull()
        assertEquals("Sign-in failed: Invalid authorization_code.", error?.message)
    }

    @Test
    fun `builds an RFC 7636 verifier and S256 challenge`() {
        val verifier = WpcomAuth.newCodeVerifier()
        assertEquals(43, verifier.length)
        assertNotEquals(verifier, WpcomAuth.newCodeVerifier())
        // Test vector from RFC 7636 appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            WpcomAuth.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }
}
