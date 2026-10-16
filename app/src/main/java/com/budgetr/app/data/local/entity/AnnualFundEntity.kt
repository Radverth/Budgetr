package com.budgetr.app.data.local.entity

import androidx.room.Entity

/** Money being saved up for one yearly bill, keyed by account + description (trimmed, lower
 *  case) like bill due days. Device-local, saved in the phone backup. */
@Entity(tableName = "annual_funds", primaryKeys = ["account", "infoKey"])
data class AnnualFundEntity(
    val account: String,
    val infoKey: String,
    val saved: Double,
    val processedPeriod: String?,
    val lastSetAside: Double,
    val lastReleased: Double
)
