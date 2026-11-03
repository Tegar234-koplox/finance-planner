package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import org.junit.Rule
import org.junit.Test

class LanguageUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun text(tag: String, expected: String) = compose.waitUntil(60_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { item -> item.text } == expected
        }
    }
    private fun waitText(expected: String) = compose.waitUntil(60_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
    @Test fun indonesianAndEnglishCoverNavigationTransactionsReportsForecastsAndPersistedChoice() {
        ready()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language_id").performScrollTo().performClick()
        waitText("Pengaturan")
        compose.activityRule.scenario.recreate(); ready(); waitText("Pengaturan")
        compose.onNodeWithTag("language_id").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Keuangan Anda").assertIsDisplayed()
        compose.onNodeWithTag("nav_budget").performClick()
        compose.onNodeWithText("Anggaran bulanan").assertIsDisplayed()
        compose.onNodeWithTag("nav_plan").performClick()
        compose.onNodeWithText("Prakiraan").assertIsDisplayed()
        compose.onNodeWithTag("forecast_list").performScrollToNode(hasTestTag("forecast_evidence"))
        compose.onNodeWithTag("forecast_evidence").assertTextEquals("Bukti: TIDAK TERSEDIA")
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_reports").performClick()
        compose.onNodeWithText("Laporan").assertIsDisplayed()
        compose.onNodeWithTag("reports_list").performScrollToNode(hasTestTag("report_comparison"))
        compose.onNodeWithTag("report_comparison").assertTextContains("Tidak tersedia", substring = true)
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_add").performClick()
        compose.onNodeWithText("Jenis: Pengeluaran").assertExists()
        compose.onNodeWithTag("transaction_amount").performTextInput("10.000,50")
        val note = "Catatan pribadi / English text ${System.nanoTime()}"
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Simpan transaksi").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_expenses"))
        text("home_expenses", "Pengeluaran konsumsi: " + MoneyText.format(Money(1000050)))
        compose.onNodeWithContentDescription("Pengaturan").performClick()
        compose.onNodeWithTag("language_en").performScrollTo().performClick(); waitText("Settings")
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).assertExists()
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasAnyChild(hasText(note)))).performScrollTo().performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Your finances").assertIsDisplayed()
    }
}
