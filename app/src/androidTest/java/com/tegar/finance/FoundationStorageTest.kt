package com.tegar.finance

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.data.repository.LocalFoundationRepository
import com.tegar.finance.domain.model.AppResult
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class FoundationStorageTest {
    @Test fun initializationIsAtomicIdempotentAndSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "foundation-test-${UUID.randomUUID()}.db"
        val clock = Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC)
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        var db = open()
        try {
            var repository = LocalFoundationRepository(db, clock)
            coroutineScope { List(8) { async { repository.initialize() } }.awaitAll().forEach { assertTrue(it is AppResult.Success) } }
            val ledger = (repository.observeLedger().first() as AppResult.Success).value!!
            assertEquals(LocalDate.of(2026, 10, 7), ledger.trackedFromDate)
            assertEquals("IDR", ledger.currency.value)
            assertEquals(11, (repository.observeCategories().first() as AppResult.Success).value.size)
            db.close()
            db = open()
            repository = LocalFoundationRepository(db, clock)
            assertTrue(repository.initialize() is AppResult.Success)
            assertEquals(ledger, (repository.observeLedger().first() as AppResult.Success).value)
            assertEquals(11, (repository.observeCategories().first() as AppResult.Success).value.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
