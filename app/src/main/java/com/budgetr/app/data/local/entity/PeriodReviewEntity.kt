package com.budgetr.app.data.local.entity

import androidx.room.Entity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "period_reviews", primaryKeys = ["spreadsheetId", "periodStart"])
data class PeriodReviewEntity(
    val spreadsheetId: String,
    val periodStart: String,
    val planJson: String? = null,
    val snapshotJson: String? = null,
    val complete: Boolean = false,
    val receiptJson: String? = null
)
