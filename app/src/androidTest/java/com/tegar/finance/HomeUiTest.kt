package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class HomeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun homeTag(tag: String) {
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun value(tag: String, expected: String) {
        homeTag(tag)
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } == expected
            }
        }
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }
    private fun saved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty()
    }
    private fun add(type: String, amount: String, note: String) {
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_add").performClick()
        if (type != "Expense") {
            compose.onNodeWithText("Type: Expense").performClick()
            compose.onNodeWithText(type, useUnmergedTree = true).performClick()
        }
        compose.onNodeWithTag("transaction_amount").performTextInput(amount)
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick(); saved()
        compose.onNodeWithContentDescription("Navigate back").performClick()
    }
    private fun history(note: String) {
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(note))
    }
    private fun action(label: String, note: String) =
        compose.onNode(hasText(label) and hasAnyAncestor(hasAnyChild(hasText(note))))
    private fun delete(note: String) {
        history(note); action("Delete", note).performScrollTo().performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
    }
    @Test fun homeTracksIncomeExpenseContributionBudgetAndEditsWithoutInventingFutureMetrics() {
        ready()
        val suffix = System.nanoTime()
        val income = "Home income $suffix"; val expense = "Home expense $suffix"; val investment = "Home investment $suffix"
        value("home_cash", MoneyText.format(Money(0)))
        add("Income", "40.000", income)
        add("Expense", "10.000", expense)
        add("Investment", "5.000", investment)
        value("home_cash", MoneyText.format(Money(2500000)))
        value("home_income", "Income: " + MoneyText.format(Money(4000000)))
        value("home_expenses", "Consumption expenses: " + MoneyText.format(Money(1000000)))
        value("home_contributions", "Investment contributions: " + MoneyText.format(Money(500000)))
        value("home_saving", "Income minus consumption: " + MoneyText.format(Money(3000000)))
        value("home_saving_rate", "Saving rate: 75.00%")
        value("home_daily_limit", "Not available")
        compose.onNodeWithText("Review budget").performScrollTo().performClick()
        compose.onNodeWithTag("budget_list").performScrollToNode(hasText("Set overall budget"))
        compose.onNodeWithText("Set overall budget").performScrollTo().performClick()
        compose.onNodeWithTag("budget_amount").performTextInput("100.000")
        compose.onNodeWithText("Save budget").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("budget_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("budget_list").performScrollToIndex(0)
        compose.onNodeWithTag("budget_month").performTextReplacement("2099-02")
        compose.onNodeWithText("Open month").performClick()
        compose.onNodeWithTag("nav_home").performClick()
        homeTag("home_daily_limit")
        compose.onNodeWithText("Review budget").performScrollTo().performClick()
        compose.onNodeWithTag("budget_list").performScrollToIndex(0)
        compose.onNodeWithTag("budget_month").assertTextContains(YearMonth.now(ZoneId.of("Asia/Jakarta")).toString())
        compose.onNodeWithTag("nav_home").performClick()
        value("home_remaining", "Remaining after recorded expenses: " + MoneyText.format(Money(9000000)))
        compose.activityRule.scenario.recreate(); ready()
        value("home_cash", MoneyText.format(Money(2500000)))
        history(expense); action("Edit", expense).performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("transaction_amount").performTextReplacement("15.000")
        compose.onNodeWithText("Save transaction").performClick(); saved()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        value("home_cash", MoneyText.format(Money(2000000)))
        value("home_remaining", "Remaining after recorded expenses: " + MoneyText.format(Money(8500000)))
        delete(investment)
        value("home_cash", MoneyText.format(Money(2500000)))
        value("home_saving", "Income minus consumption: " + MoneyText.format(Money(2500000)))
        delete(expense); delete(income)
        value("home_cash", MoneyText.format(Money(0)))
        value("home_saving_rate", "Saving rate: Not available")
        homeTag("home_investments")
        compose.onNodeWithTag("home_investments").assertIsDisplayed()
        compose.onNodeWithTag("nav_budget").performClick()
        compose.onNodeWithTag("budget_list").performScrollToNode(hasTestTag("overall_remaining"))
        compose.onNode(hasText("Remove") and hasAnyAncestor(hasTestTag("budget_overall"))).performScrollTo().performClick()
        compose.onNodeWithText("Remove allocation").performClick()
        compose.onNodeWithTag("nav_home").performClick()
        value("home_allocation", "Overall allocation: Not set")
    }
}
