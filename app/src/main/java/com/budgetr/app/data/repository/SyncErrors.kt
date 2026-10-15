package com.budgetr.app.data.repository

import retrofit2.HttpException
import java.io.IOException

/** Sorts sync failures into "try again later" and "Google refused this change". */
object SyncErrors {

    /** No connection, a timeout, an expired sign-in or a busy/broken server: keep it queued. */
    fun isRetryable(e: Throwable): Boolean = when (e) {
        is IOException -> true
        is HttpException -> e.code() == 401 || e.code() == 408 || e.code() == 429 || e.code() >= 500
        else -> false
    }

    /** Plain-English reason shown next to a refused change. */
    fun reason(e: Throwable): String = when (e) {
        is RowMovedException -> "This transaction was changed or removed in your Google Sheet."
        is HttpException -> "Google Sheets refused this change (error ${e.code()})."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Google Sheets couldn't save this change."
    }
}
