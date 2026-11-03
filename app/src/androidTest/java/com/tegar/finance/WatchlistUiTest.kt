package com.tegar.finance

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.market.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.MarketViewModel
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WatchlistUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun closeSoftKeyboard() = compose.runOnIdle {
        compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
    }
    @Test fun realWatchlistCrudProtectedKeyLanguageAndRecreationNeverFetchUntilExplicitRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keyStore = KeystoreProviderKeyStore(context)
        val secret = "synthetic_ui_key_123456789"
        val prefix = "UJI-${UUID.randomUUID().toString().take(8)}"
        val db = Room.databaseBuilder(context, MarketDatabase::class.java, "market-cache.db").addMigrations(MarketMigrations.FROM_1_TO_2).build()
        val repo = RoomWatchlistRepository(db, Clock.systemUTC())
        runBlocking { assertEquals(AppResult.Success(null), keyStore.read()) }
        try {
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav_market").performClick()
            compose.onNodeWithTag("market_add").performClick()
            compose.onNodeWithTag("market_name").performTextInput(prefix)
            compose.onNodeWithTag("market_symbol").performTextInput("aapl"); closeSoftKeyboard()
            compose.onNodeWithTag("market_save").performClick()
            fun model() = ViewModelProvider(compose.activity)[MarketViewModel::class.java]
            compose.waitUntil(60_000) { model().state.value.rows.any { it.entry?.name == prefix } }
            var row = model().state.value.rows.first { it.entry?.name == prefix }
            assertNull(row.record.quote); assertFalse(model().state.value.canRefresh)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("market_screen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("market_menu_${row.entry!!.id}").performClick()
            compose.onNodeWithText("Edit instrument").performClick()
            compose.onNodeWithTag("market_symbol").performTextReplacement("MSFT"); closeSoftKeyboard()
            compose.onNodeWithTag("market_save").performClick()
            compose.waitUntil(60_000) { model().state.value.rows.any { it.entry?.name == prefix && it.record.key.symbol == "MSFT" } }
            compose.onNodeWithTag("market_connection").performClick()
            compose.onNodeWithTag("market_api_key").performTextInput("invalid space"); closeSoftKeyboard()
            compose.onNodeWithTag("market_save_key").performClick(); compose.onNodeWithTag("market_form_error").assertExists()
            compose.onNodeWithTag("market_api_key").performTextReplacement(secret); closeSoftKeyboard()
            compose.onNodeWithTag("market_save_key").performClick()
            compose.waitUntil(60_000) { model().state.value.hasKey && model().state.value.keyEditor == null }
            assertFalse(model().state.value.toString().contains(secret))
            runBlocking { assertTrue(db.quotes().observe().first().isEmpty()); assertNull(db.watchlist().window("twelvedata")) }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("language_id").performScrollTo().performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Kembali").performClick()
            compose.onNodeWithText("Twelve Data · API key tersimpan").assertExists()
            compose.onNodeWithContentDescription("Pengaturan").performClick()
            compose.onNodeWithTag("language_en").performScrollTo().performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Navigate back").performClick()
            compose.onNodeWithTag("market_connection").performClick()
            compose.onNodeWithTag("market_disconnect").performScrollTo().performClick()
            compose.onNodeWithTag("market_confirm_remove").performClick()
            compose.waitUntil(60_000) { !model().state.value.hasKey && !model().state.value.disconnect }
            row = model().state.value.rows.first { it.entry?.name == prefix }
            compose.onNodeWithTag("market_menu_${row.entry!!.id}").performClick(); compose.onNodeWithText("Remove").performClick()
            compose.onNodeWithTag("market_confirm_remove").performClick()
            compose.waitUntil(60_000) { model().state.value.rows.none { it.entry?.name == prefix } }
            runBlocking { assertEquals(AppResult.Success(null), keyStore.read()) }
        } finally {
            runBlocking {
                val rows = (repo.observe().first() as AppResult.Success).value
                rows.filter { it.name.startsWith(prefix) }.forEach { repo.delete(it.id) }
                if ((keyStore.read() as? AppResult.Success)?.value == secret) keyStore.clear()
            }
            db.close()
        }
    }
}
