package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.GoalEntity

@Dao
interface GoalDao {
    @Query("SELECT * FROM finance_goal WHERE id = :id AND ledgerId = 1")
    suspend fun get(id: String): GoalEntity?
    @Insert suspend fun insert(row: GoalEntity)
    @Update suspend fun update(row: GoalEntity): Int
    @Query("DELETE FROM finance_goal WHERE id = :id AND ledgerId = 1")
    suspend fun delete(id: String): Int
}
