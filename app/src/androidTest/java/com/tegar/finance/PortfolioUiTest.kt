package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import org.junit.Rule
import org.junit.Test

class PortfolioUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun scroll(tag: String) { compose.onNodeWithTag("portfolio_list").performScrollToNode(hasTestTag(tag)); compose.onNodeWithTag(tag).performScrollTo() }
    private fun value(tag: String, amount: Long) {
        scroll(tag)
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { text -> text.text } == MoneyText.format(Money(amount))
        } }
    }
    private fun saved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("portfolio_editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        listOf("holding_name", "investment_amount", "contribution_plan_amount", "allocation_input_CASH").all { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isEmpty() }
    }
    private fun top() = compose.onNodeWithTag("portfolio_list").performScrollToIndex(0)
    private fun prefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") { it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix) }
    private fun id(tagPrefix: String, text: String): String {
        compose.onNodeWithTag("portfolio_list").performScrollToNode(hasText(text))
        return compose.onNode(prefix(tagPrefix) and hasAnyDescendant(hasText(text))).fetchSemanticsNode().config[SemanticsProperties.TestTag].removePrefix(tagPrefix)
    }
    private fun movement(name: String, action: String, amount: String, after: String, note: String): String {
        top(); compose.onNodeWithTag("portfolio_tab_2").performClick(); scroll("investment_movement_add")
        compose.onNodeWithTag("investment_movement_add").performClick()
        compose.onNodeWithTag("investment_action").performClick(); compose.onNodeWithTag("investment_action_option_${if (action == "Redemption") 1 else 0}").performClick()
        compose.onNodeWithTag("investment_holding").performScrollTo().performClick(); compose.onNodeWithTag("investment_holding_option_1").performClick()
        compose.onNodeWithTag("investment_holding").assertTextEquals(name)
        compose.onNodeWithTag("investment_amount").performScrollTo().performTextInput(amount)
        compose.onNodeWithTag("investment_value_after").performScrollTo().performTextInput(after)
        compose.onNodeWithTag("investment_note").performScrollTo().performTextInput(note); compose.onNodeWithTag("investment_note").performImeAction()
        compose.onNodeWithTag("investment_movement_save").performClick(); saved()
        return id("movement_", note)
    }
    @Test fun holdingsTargetsContributionsRedemptionAndSnapshotsRemainConsistentAcrossRecreationAndLanguage() {
        ready(); val suffix = System.nanoTime(); val name = "Fund $suffix"; val income = "Portfolio income $suffix"
        compose.onNodeWithTag("home_add").performClick()
        compose.onNodeWithText("Type: Expense").performClick(); compose.onNodeWithText("Income", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction_amount").performTextInput("2.000")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(income); compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_investments")); compose.onNodeWithTag("home_investments").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("holding_add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("holding_add").performClick()
        compose.onNodeWithTag("holding_name").performTextInput(name)
        compose.onNodeWithTag("holding_bucket").performScrollTo().performClick(); compose.onNodeWithText("Funds", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("holding_amount").performScrollTo().performTextInput("1.000")
        compose.onNodeWithTag("holding_date").performScrollTo().performImeAction(); compose.onNodeWithTag("holding_save").performClick(); saved()
        val holdingId = id("holding_", name)
        value("portfolio_total", 300000); value("portfolio_cash", 200000)
        top(); compose.onNodeWithTag("portfolio_tab_1").performClick(); scroll("allocation_edit"); compose.onNodeWithTag("allocation_edit").performClick()
        compose.onNodeWithTag("allocation_input_CASH").performTextReplacement("60")
        compose.onNodeWithTag("allocation_input_FUND").performScrollTo().performTextReplacement("40")
        compose.onNodeWithTag("allocation_input_OTHER").performScrollTo().performImeAction(); compose.onNodeWithTag("allocation_save").performClick(); saved()
        top(); compose.onNodeWithTag("portfolio_tab_2").performClick(); scroll("contribution_plan_edit"); compose.onNodeWithTag("contribution_plan_edit").performClick()
        compose.onNodeWithTag("contribution_plan_amount").performTextInput("500"); compose.onNodeWithTag("contribution_plan_amount").performImeAction()
        compose.onNodeWithTag("contribution_plan_save").performClick(); saved()
        val deposit = movement(name, "Contribution", "200", "1.200", "Deposit $suffix")
        value("portfolio_total", 300000); value("portfolio_cash", 180000)
        val redemption = movement(name, "Redemption", "300", "900", "Redeem $suffix")
        value("portfolio_total", 300000); value("portfolio_cash", 210000); value("portfolio_invested", 90000)
        value("contribution_gross", 20000); value("contribution_redeemed", 30000); value("contribution_net", -10000)
        scroll("contribution_progress"); compose.onNodeWithTag("contribution_progress").assertTextEquals("Target progress: 40.00%")
        compose.activityRule.scenario.recreate(); ready(); value("portfolio_total", 300000)
        top(); compose.onNodeWithTag("portfolio_tab_1").performClick(); scroll("allocation_actual_CASH")
        compose.onNodeWithTag("allocation_actual_CASH").assertTextEquals("Actual: 70.00%")
        compose.onNodeWithContentDescription("Settings").performClick(); compose.onNodeWithTag("language_id").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Kembali").performClick(); top(); compose.onNodeWithText("Investasi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pengaturan").performClick(); compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_wealth")); compose.onNodeWithTag("home_wealth").performScrollTo().performClick()
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag("wealth_total")); compose.onNodeWithTag("wealth_total").assertTextEquals(MoneyText.format(Money(300000)))
        compose.onNodeWithTag("wealth_capture").performScrollTo().performClick(); compose.onNodeWithTag("wealth_confirm_capture").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("wealth_exists").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("wealth_list").performScrollToNode(prefix("wealth_history_"))
        val captureId = compose.onAllNodes(prefix("wealth_history_")).fetchSemanticsNodes().single { it.config[SemanticsProperties.TestTag].matches(Regex("wealth_history_[a-f0-9-]{36}")) }
            .config[SemanticsProperties.TestTag].removePrefix("wealth_history_")
        compose.onNodeWithContentDescription("Navigate back").performClick(); compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_investments"))
        compose.onNodeWithTag("home_investments").performScrollTo().performClick(); top(); compose.onNodeWithTag("portfolio_tab_2").performClick()
        for (flow in listOf(deposit, redemption)) { scroll("movement_delete_$flow"); compose.onNodeWithTag("movement_delete_$flow").performClick(); compose.onNodeWithTag("portfolio_delete_confirm").performClick() }
        value("portfolio_cash", 200000); value("portfolio_invested", 90000)
        scroll("contribution_plan_remove"); compose.onNodeWithTag("contribution_plan_remove").performClick()
        top(); compose.onNodeWithTag("portfolio_tab_1").performClick(); scroll("allocation_edit"); compose.onNodeWithText("Clear targets").performScrollTo().performClick()
        top(); compose.onNodeWithTag("portfolio_tab_0").performClick(); scroll("holding_more_$holdingId"); compose.onNodeWithTag("holding_more_$holdingId").performClick()
        compose.onNodeWithTag("holding_delete_$holdingId").performClick(); compose.onNodeWithTag("portfolio_delete_confirm").performClick()
        value("portfolio_total", 200000)
        compose.onNodeWithContentDescription("Navigate back").performClick(); compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_wealth"))
        compose.onNodeWithTag("home_wealth").performScrollTo().performClick()
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag("wealth_saved_total_$captureId")); compose.onNodeWithTag("wealth_saved_total_$captureId").performScrollTo().assertTextEquals(MoneyText.format(Money(300000)))
        compose.onNodeWithTag("wealth_list").performScrollToNode(hasTestTag("wealth_delete_capture_$captureId")); compose.onNodeWithTag("wealth_delete_capture_$captureId").performScrollTo().performClick()
        compose.onNodeWithTag("wealth_confirm_delete").performClick(); compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("home_list").performScrollToIndex(0); compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(income))
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasAnyChild(hasText(income)))).performScrollTo().performClick(); compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(income).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick(); compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_cash"))
        compose.onNodeWithTag("home_cash").assertTextEquals(MoneyText.format(Money(0)))
    }
}
