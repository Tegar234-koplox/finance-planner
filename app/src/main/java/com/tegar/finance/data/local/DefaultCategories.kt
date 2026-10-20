package com.tegar.finance.data.local

import com.tegar.finance.data.local.entity.CategoryEntity
import com.tegar.finance.domain.model.TransactionType
import java.util.Locale
import java.util.UUID

/** Data seeds, never category switches inside the UI. */
object DefaultCategories {
    fun create(createdAt: Long): List<CategoryEntity> {
        val expense = listOf("food" to "Food / Jajan", "fuel" to "Fuel / Bensin",
            "cigarettes" to "Cigarettes / Rokok", "bills" to "Bills",
            "transport" to "Transportation", "health" to "Health",
            "entertainment" to "Entertainment", "other-expense" to "Other / Lain-lain")
        val seeds = expense.map { Triple(it.first, it.second, TransactionType.EXPENSE) } +
            listOf(Triple("salary", "Salary", TransactionType.INCOME),
                Triple("other-income", "Other Income", TransactionType.INCOME),
                Triple("investment", "Investment Contribution", TransactionType.INVESTMENT))
        return seeds.mapIndexed { index, (key, name, type) ->
            CategoryEntity(UUID.nameUUIDFromBytes("finance/category/$key".toByteArray(Charsets.UTF_8)).toString(),
                name, name.lowercase(Locale.ROOT), type.name, true, index, key, createdAt)
        }
    }
}
