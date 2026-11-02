package com.tegar.finance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.ui.AppLocalization
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PortfolioContentUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun zeroUnknownAndReactiveValuesStayHonestWithManualTargetsAndIndonesianLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC); val today = LocalDate.of(2028, 3, 10)
        val repo = LocalInvestmentRepository(db, clock); val store = ViewModelStore()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        var id = ""
        try {
            runBlocking {
                LocalFoundationRepository(db, clock).initialize().ok()
                id = repo.saveHolding(null, HoldingDraft("Fund", "", AllocationBucket.FUND, today), HoldingValueDraft("", today, Money(0))).ok()
                repo.saveTargets(AllocationBucket.entries.map { AllocationTarget(it, if (it == AllocationBucket.FUND) 10000 else 0) }).ok()
            }
            val model = PortfolioViewModel(repo, clock).also { store.put("portfolio", it) }
            val language = mutableStateOf(AppLanguage.ENGLISH)
            compose.setContent { AppLocalization(language.value) { MaterialTheme { PortfolioScreen(model.state.collectAsStateWithLifecycle().value, model) } } }
            compose.waitUntil(60_000) { model.state.value.overview != null }
            compose.onNodeWithTag("portfolio_total").assertTextEquals(MoneyText.format(Money(0)))
            compose.onNodeWithTag("portfolio_tab_1").performClick()
            compose.onNodeWithTag("portfolio_list").performScrollToNode(hasTestTag("allocation_unavailable"))
            compose.onNodeWithTag("allocation_unavailable").performScrollTo().assertExists()
            runBlocking {
                val value = repo.observePortfolio().first().ok()!!.values.single()
                repo.saveValue(value.id, HoldingValueDraft(id, today, Money(10000))).ok()
            }
            compose.waitUntil(60_000) { model.state.value.overview?.valuation?.total == Money(10000) }
            compose.onNodeWithTag("portfolio_list").performScrollToNode(hasTestTag("allocation_actual_FUND"))
            compose.onNodeWithTag("allocation_actual_FUND").performScrollTo().assertTextEquals("Actual: 100.00%")
            compose.runOnIdle { language.value = AppLanguage.INDONESIAN }
            compose.onNodeWithTag("allocation_actual_FUND").assertTextEquals("Aktual: 100.00%")
            runBlocking { repo.deleteValue(repo.observePortfolio().first().ok()!!.values.single().id).ok() }
            compose.waitUntil(60_000) { model.state.value.overview?.valuation?.missing == 1 }
            compose.onNodeWithTag("portfolio_list").performScrollToIndex(0)
            compose.onNodeWithTag("portfolio_total").assertTextEquals("Tidak tersedia")
            assertEquals(10000, model.state.value.source!!.targets.sumOf { it.basisPoints })
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
}
