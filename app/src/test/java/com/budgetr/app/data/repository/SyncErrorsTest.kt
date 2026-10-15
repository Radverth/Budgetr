package com.budgetr.app.data.repository

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

class SyncErrorsTest {

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody(null)))

    @Test fun `no connection and server trouble wait for later`() {
        assertTrue(SyncErrors.isRetryable(UnknownHostException("no signal")))
        assertTrue(SyncErrors.isRetryable(IOException("timeout")))
        assertTrue(SyncErrors.isRetryable(http(503)))
        assertTrue(SyncErrors.isRetryable(http(429)))
        assertTrue(SyncErrors.isRetryable(http(401)))
    }

    @Test fun `refusals and missing rows need a decision`() {
        assertFalse(SyncErrors.isRetryable(http(400)))
        assertFalse(SyncErrors.isRetryable(RowMovedException()))
        assertEquals("Google Sheets refused this change (error 400).", SyncErrors.reason(http(400)))
        assertEquals("This transaction was changed or removed in your Google Sheet.", SyncErrors.reason(RowMovedException()))
    }
}
