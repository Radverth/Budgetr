package com.budgetr.app.data.repository

import com.budgetr.app.data.local.dao.CategoryRuleDao
import com.budgetr.app.data.local.entity.CategoryRuleEntity
import com.budgetr.app.data.model.CategoryRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Category rules kept on the phone (and in the phone backup). */
@Singleton
class CategoryRuleRepository @Inject constructor(private val dao: CategoryRuleDao) {

    fun getRules(): Flow<List<CategoryRule>> = dao.getAll().map { rows -> rows.map { CategoryRule(it.id, it.pattern, it.tag) } }

    suspend fun save(rule: CategoryRule) = dao.upsert(CategoryRuleEntity(rule.id, rule.pattern.trim(), rule.tag.trim()))

    suspend fun delete(id: Long) = dao.delete(id)
}
