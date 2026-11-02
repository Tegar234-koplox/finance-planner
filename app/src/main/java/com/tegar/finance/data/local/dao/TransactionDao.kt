package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("""SELECT * FROM finance_transaction
        WHERE (:from IS NULL OR date >= :from) AND (:through IS NULL OR date <= :through)
        AND (:type IS NULL OR transactionType = :type) AND (:category IS NULL OR categoryId = :category)
        ORDER BY date DESC, createdAt DESC, id DESC""")
    fun observe(from: Long?, through: Long?, type: String?, category: String?): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM finance_transaction WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?
    @Insert suspend fun insert(row: TransactionEntity)
    @Update suspend fun update(row: TransactionEntity): Int
    @Query("DELETE FROM finance_transaction WHERE id = :id")
    suspend fun delete(id: String): Int
    @Query("SELECT EXISTS(SELECT 1 FROM finance_transaction WHERE categoryId = :id)")
    suspend fun categoryInUse(id: String): Boolean
    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun category(id: String): CategoryEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM category WHERE transactionType = :type AND normalizedName = :name AND (:exceptId IS NULL OR id != :exceptId))")
    suspend fun categoryNameExists(type: String, name: String, exceptId: String?): Boolean
    @Insert suspend fun insertCategory(row: CategoryEntity)
    @Update suspend fun updateCategory(row: CategoryEntity): Int
    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteCategory(id: String): Int
}
