package com.budgetr.app.util

import com.budgetr.app.data.repository.RowMovedException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Plain-English messages for errors, instead of raw text like "HTTP 401" or "Unable to resolve host". */
object UserMessages {

    const val OFFLINE = "Couldn't reach Google Sheets. Check your connection. Anything you add is saved on your phone and sent later."
    const val SIGN_IN = "Your Google sign-in needs renewing. Close Budgetr and open it again to sign back in."
    const val NOT_FOUND = "Couldn't find your Google Sheet. It may have been moved or deleted. Choose it again in Settings."
    const val BUSY = "Google Sheets is busy right now. Try again in a minute."
    const val GENERIC = "Something went wrong talking to Google Sheets. Pull down to try again."

    fun from(e: Throwable?): String = when (e) {
        null -> GENERIC
        is RowMovedException -> e.message ?: GENERIC
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> OFFLINE
        is HttpException -> when (e.code()) {
            401, 403 -> SIGN_IN
            404 -> NOT_FOUND
            408, 429, in 500..599 -> BUSY
            else -> GENERIC
        }
        is IOException -> OFFLINE
        else -> GENERIC
    }
}
