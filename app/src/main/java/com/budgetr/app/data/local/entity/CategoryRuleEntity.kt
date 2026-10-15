package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** "Description contains [pattern] → [tag]". Device-local, saved in the phone backup. */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val tag: String
)
