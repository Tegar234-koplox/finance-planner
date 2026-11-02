package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WealthDao {
    @Transaction @Query("SELECT * FROM ledger WHERE id=1") fun observeSource(): Flow<WealthSourceEntity?>
    @Transaction @Query("SELECT * FROM ledger WHERE id=1") suspend fun loadSource(): WealthSourceEntity?
    @Query("SELECT * FROM wealth_item WHERE id=:id AND ledgerId=1") suspend fun item(id: String): WealthItemEntity?
    @Query("SELECT * FROM wealth_value WHERE id=:id") suspend fun value(id: String): WealthValueEntity?
    @Query("SELECT * FROM wealth_value WHERE itemId=:itemId AND date=:date") suspend fun valueAt(itemId: String, date: Long): WealthValueEntity?
    @Query("SELECT * FROM wealth_value WHERE itemId=:itemId") suspend fun values(itemId: String): List<WealthValueEntity>
    @Insert suspend fun insertItem(row: WealthItemEntity)
    @Update suspend fun updateItem(row: WealthItemEntity): Int
    @Insert suspend fun insertValue(row: WealthValueEntity)
    @Update suspend fun updateValue(row: WealthValueEntity): Int
    @Query("DELETE FROM wealth_item WHERE id=:id AND ledgerId=1") suspend fun deleteItem(id: String): Int
    @Query("DELETE FROM wealth_value WHERE id=:id") suspend fun deleteValue(id: String): Int
    @Insert suspend fun insertCapture(row: WealthCaptureEntity)
    @Insert suspend fun insertLines(rows: List<WealthCaptureLineEntity>)
    @Query("DELETE FROM wealth_capture WHERE id=:id AND ledgerId=1") suspend fun deleteCapture(id: String): Int
}
