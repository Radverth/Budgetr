package com.budgetr.app.data.model

/** "Description contains [pattern] → spending category [tag]", e.g. TESCO → Groceries. */
data class CategoryRule(
    val id: Long = 0,
    val pattern: String,
    val tag: String
)
