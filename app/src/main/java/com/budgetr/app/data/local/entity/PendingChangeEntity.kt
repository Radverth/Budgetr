package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One queued add, edit or delete waiting to reach the Google Sheet. */
@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val account: String,
    val txId: String?,
    val rowIndex: Int,
    val date: String,
    val info: String,
    val amount: Double,
    val category: String,
    val activeMonths: String?,
    val tag: String?,
    val failedReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
