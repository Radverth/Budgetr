package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A per-pay-period budget for one spending category. Device-local only, like category caps. */
@Entity(tableName = "envelopes")
data class EnvelopeEntity(
    @PrimaryKey val tag: String,
    val limitAmount: Double,
    val rollover: Boolean,
    val carriedOver: Double,
    /** dd/MM/yyyy start of the pay period [carriedOver] was worked out for. Stops a retried
     *  pay-period rollover from carrying the same money twice. */
    val carriedForPeriod: String?
)
