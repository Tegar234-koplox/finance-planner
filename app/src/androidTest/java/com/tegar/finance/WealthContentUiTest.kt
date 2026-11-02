package com.tegar.finance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
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

class WealthContentUiTest {
    @get:Rule val compose = createComposeRule()
    private fun value(tag: String, expected: String) {
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } == expected
        } }
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }
    @Test fun liveValuesReactButSavedCashNamesAndDatedComponentsStayFrozenAcrossLocaleAndDeletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        val store = ViewModelStore(); val date = LocalDate.of(2028, 3, 10)
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        val repo = LocalWealthRepository(db, clock)
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        var itemId = ""; var captureId = ""
        val draft = WealthItemDraft("Vehicle / Kendaraan", WealthKind.ASSET, WealthCategory.VEHICLE, date.minusYears(1), Money(999000))
        try {
            runBlocking {
                LocalFoundationRepository(db, clock).initialize().ok()
                itemId = repo.saveItem(null, draft, WealthValueDraft("", date.minusDays(7), Money(150000))).ok()
                captureId = repo.captureToday().ok()
            }
            val model = WealthViewModel(repo, clock).also { store.put("wealth", it) }
            val language = mutableStateOf(AppLanguage.ENGLISH)
            compose.setContent { AppLocalization(language.value) { MaterialTheme { WealthScreen(model.state.collectAsStateWithLifecycle().value, model) } } }
            compose.waitUntil(60_000) { model.state.value.overview != null }
            value("wealth_total", MoneyText.format(Money(150000)))
            value("wealth_saved_total_$captureId", MoneyText.format(Money(150000)))
            compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag("wealth_components_$captureId"))
            compose.onNodeWithTag("wealth_components_$captureId").performScrollTo().performClick()
            compose.onNodeWithText("Vehicle / Kendaraan · Asset · ${MoneyText.format(Money(150000))} · valued on 2028-03-03").performScrollTo().assertIsDisplayed()
            runBlocking {
                repo.saveItem(itemId, draft.copy(name = "Renamed now")).ok()
                repo.saveValue(null, WealthValueDraft(itemId, date, Money(120000))).ok()
            }
            compose.waitUntil(60_000) { model.state.value.overview?.total == Money(120000) }
            value("wealth_total", MoneyText.format(Money(120000)))
            value("wealth_saved_total_$captureId", MoneyText.format(Money(150000)))
            compose.runOnIdle { language.value = AppLanguage.INDONESIAN }
            compose.onNodeWithTag("wealth_list").performScrollToIndex(0)
            compose.onNodeWithText("Aset dan kekayaan bersih tercatat").assertIsDisplayed()
            runBlocking { repo.deleteItem(itemId).ok() }
            compose.waitUntil(60_000) { model.state.value.source?.items?.isEmpty() == true }
            value("wealth_total", MoneyText.format(Money(0)))
            value("wealth_saved_total_$captureId", MoneyText.format(Money(150000)))
            assertEquals("Vehicle / Kendaraan", runBlocking { repo.observeWealth().first().ok()!!.captures.single().lines.single().name })
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
}
