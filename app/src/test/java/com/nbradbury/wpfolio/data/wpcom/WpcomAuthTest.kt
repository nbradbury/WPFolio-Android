package com.nbradbury.wpfolio.data.wpcom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WpcomAuthTest {

    @Test
    fun `reads the token and expiry from the fragment`() {
        val token = WpcomAuth.parseRedirect(
            fragment = "access_token=abc%2B123&expires_in=1209600&token_type=bearer&site_id=0&state=s1",
            query = null,
            expectedState = "s1",
            now = 1_000L,
        )
        assertEquals("abc+123", token.accessToken)
        assertEquals(1_000L + 1_209_600_000L, token.expiresAt)
    }

    @Test
    fun `a token without expires_in never expires locally`() {
        val token = WpcomAuth.parseRedirect("access_token=abc&state=s1", null, "s1")
        assertNull(token.expiresAt)
    }

    @Test
    fun `surfaces errors from the query or the fragment`() {
        val fromQuery = runCatching {
            WpcomAuth.parseRedirect(null, "error=access_denied&error_description=You+said+no", "s1")
        }.exceptionOrNull()
        assertEquals("You said no", fromQuery?.message)

        val fromFragment = runCatching {
            WpcomAuth.parseRedirect("error=invalid_scope", null, "s1")
        }.exceptionOrNull()
        assertEquals("invalid_scope", fromFragment?.message)
    }

    @Test
    fun `rejects a mismatched or missing state`() {
        listOf("other", null).forEach { expected ->
            val error = runCatching { WpcomAuth.parseRedirect("access_token=abc&state=s1", null, expected) }.exceptionOrNull()
            assertTrue(error is IOException)
        }
    }
}
