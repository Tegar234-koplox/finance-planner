package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WealthUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun scroll(tag: String) { compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag(tag)); compose.onNodeWithTag(tag).performScrollTo() }
    private fun value(tag: String, amount: Long) {
        scroll(tag)
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } == MoneyText.format(Money(amount))
        } }
    }
    private fun saved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("wealth_editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        compose.onAllNodesWithTag("wealth_name").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("wealth_amount").fetchSemanticsNodes().isEmpty()
    }
    private fun itemId(name: String): String {
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasText(name))
        return compose.onAllNodes(hasTestTagPrefix("wealth_item_")).fetchSemanticsNodes().single {
            it.children.any { child -> child.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { text -> text.text == name } }
        }.config[SemanticsProperties.TestTag].removePrefix("wealth_item_")
    }
    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag prefix $prefix") { it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix) }
    private fun create(name: String, amount: String, debt: Boolean = false): String {
        compose.onNodeWithTag("wealth_list").performScrollToIndex(0); compose.onNodeWithTag("wealth_add").performClick()
        compose.onNodeWithTag("wealth_name").performTextInput(name)
        if (debt) { compose.onNodeWithTag("wealth_kind").performScrollTo().performClick(); compose.onNodeWithText("Liability", useUnmergedTree = true).performClick() }
        compose.onNodeWithTag("wealth_amount").performScrollTo().performTextInput(amount)
        compose.onNodeWithTag("wealth_notes").performScrollTo().performImeAction()
        compose.onNodeWithTag("wealth_save_item").performClick(); saved()
        return itemId(name)
    }
    @Test fun createAssetLiabilityCaptureEditArchiveRecreateTranslateAndDeletePreserveHomeCash() {
        ready(); compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_wealth"))
        compose.onNodeWithTag("home_wealth").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("wealth_total").fetchSemanticsNodes().isNotEmpty() }
        value("wealth_cash", 0)
        val suffix = System.nanoTime(); val asset = create("Phone $suffix", "1.000"); val debt = create("Loan $suffix", "200", true)
        value("wealth_total", 80000); value("wealth_cash", 0)
        scroll("wealth_capture"); compose.onNodeWithTag("wealth_capture").performClick(); compose.onNodeWithTag("wealth_confirm_capture").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("wealth_exists").fetchSemanticsNodes().isNotEmpty() }
        val historyTag = compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTagPrefix("wealth_history_"))
        assertNotNull(historyTag)
        val capture = compose.onAllNodes(hasTestTagPrefix("wealth_history_")).fetchSemanticsNodes().single {
            it.config[SemanticsProperties.TestTag] != "wealth_history_empty"
        }.config[SemanticsProperties.TestTag].removePrefix("wealth_history_")
        value("wealth_saved_total_$capture", 80000)
        scroll("wealth_values_$asset"); compose.onNodeWithTag("wealth_values_$asset").performClick()
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTagPrefix("wealth_edit_value_"))
        compose.onAllNodes(hasTestTagPrefix("wealth_edit_value_")).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("wealth_amount").performTextReplacement("900")
        compose.onNodeWithTag("wealth_notes").performScrollTo().performImeAction(); compose.onNodeWithTag("wealth_save_value").performClick(); saved()
        value("wealth_total", 70000); value("wealth_saved_total_$capture", 80000)
        scroll("wealth_active_$asset"); compose.onNodeWithTag("wealth_active_$asset").performClick()
        value("wealth_total", -20000); value("wealth_cash", 0)
        compose.activityRule.scenario.recreate(); ready(); value("wealth_total", -20000); value("wealth_saved_total_$capture", 80000)
        compose.onNodeWithContentDescription("Settings").performClick(); compose.onNodeWithTag("language_id").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Kembali").performClick(); compose.onNodeWithTag("wealth_list").performScrollToIndex(0)
        compose.onNodeWithText("Aset dan kekayaan bersih tercatat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pengaturan").performClick(); compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        for (id in listOf(asset, debt)) { scroll("wealth_delete_$id"); compose.onNodeWithTag("wealth_delete_$id").performClick(); compose.onNodeWithTag("wealth_confirm_delete").performClick() }
        value("wealth_total", 0); value("wealth_saved_total_$capture", 80000)
        scroll("wealth_delete_capture_$capture"); compose.onNodeWithTag("wealth_delete_capture_$capture").performClick(); compose.onNodeWithTag("wealth_confirm_delete").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("wealth_history_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_cash"))
        compose.onNodeWithTag("home_cash").assertTextEquals(MoneyText.format(Money(0)))
    }
}
