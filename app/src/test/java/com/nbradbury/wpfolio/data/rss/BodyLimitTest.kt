package com.nbradbury.wpfolio.data.rss

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyLimitTest {

    @Test
    fun `reads bodies up to the limit and strips a BOM`() {
        assertEquals("<rss/>", "﻿<rss/>".toResponseBody().stringWithLimit(limit = 64))
        assertEquals("12345", "12345".toResponseBody().stringWithLimit(limit = 5))
    }

    @Test
    fun `refuses bodies over the limit`() {
        val error = runCatching { "123456".toResponseBody().stringWithLimit(limit = 5) }.exceptionOrNull()
        assertTrue(error is LookupException)
    }
}
