package com.budgetr.app.util

import com.budgetr.app.data.repository.RowMovedException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserMessagesTest {

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody(null)))

    @Test fun `no connection`() {
        assertEquals(UserMessages.OFFLINE, UserMessages.from(UnknownHostException("sheets.googleapis.com")))
        assertEquals(UserMessages.OFFLINE, UserMessages.from(SocketTimeoutException()))
        assertEquals(UserMessages.OFFLINE, UserMessages.from(IOException("reset")))
    }

    @Test fun `google responses`() {
        assertEquals(UserMessages.SIGN_IN, UserMessages.from(http(401)))
        assertEquals(UserMessages.SIGN_IN, UserMessages.from(http(403)))
        assertEquals(UserMessages.NOT_FOUND, UserMessages.from(http(404)))
        assertEquals(UserMessages.BUSY, UserMessages.from(http(503)))
        assertEquals(UserMessages.BUSY, UserMessages.from(http(429)))
        assertEquals(UserMessages.GENERIC, UserMessages.from(http(400)))
    }

    @Test fun `app errors keep their own wording, anything else is generic`() {
        assertEquals(RowMovedException().message, UserMessages.from(RowMovedException()))
        assertEquals(UserMessages.GENERIC, UserMessages.from(IllegalStateException("java.lang.NullPointerException at ...")))
        assertEquals(UserMessages.GENERIC, UserMessages.from(null))
    }
}
