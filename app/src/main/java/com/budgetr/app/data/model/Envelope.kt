package com.budgetr.app.data.model

/** A per-pay-period budget for one spending category (tag) of one-off costs. */
data class Envelope(
    val tag: String,
    val limit: Double,
    /** Carry unspent money into the next pay period. */
    val rollover: Boolean = false,
    /** Amount carried in from the previous pay period. */
    val carriedOver: Double = 0.0
)
