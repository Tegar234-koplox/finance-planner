package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.presentation.BookkeepingViewModel
import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Rule
import org.junit.Test

class BudgetUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun tag(value: String) {
        compose.onNodeWithTag("budget_list").performScrollToNode(hasTestTag(value))
        compose.onNodeWithTag(value).performScrollTo()
    }
    private fun action(label: String, card: String) = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(card)))
    private fun saved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("budget_editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        compose.onAllNodesWithTag("budget_amount").fetchSemanticsNodes().isEmpty()
    }
    @Test fun budgetsShowLiveConsumptionEnforceCapAndSurviveRecreation() {
        ready()
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("add_record").performClick()
        compose.onNodeWithTag("transaction_amount").performTextInput("25.000")
        val note = "Budget UI ${System.nanoTime()}"
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick()
        compose.waitUntil(60_000) {
            val errors = compose.onAllNodesWithTag("editor_error").fetchSemanticsNodes()
            if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
            compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty()
        }
        var foodId = ""
        compose.activityRule.scenario.onActivity {
            foodId = ViewModelProvider(it)[BookkeepingViewModel::class.java].state.value.categories
                .first { category -> category.active && category.transactionType == TransactionType.EXPENSE }.id
        }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("nav_budget").performClick()
        compose.onNodeWithTag("budget_list").performScrollToNode(hasText("Set overall budget"))
        compose.onNodeWithText("Set overall budget").performScrollTo().performClick()
        compose.onNodeWithTag("budget_amount").performTextInput("100.000")
        compose.onNodeWithText("Save budget").performClick(); saved()
        compose.onNodeWithText("Add category budget").performScrollTo().performClick()
        compose.onNodeWithTag("budget_amount").performTextInput("20.000")
        compose.onNodeWithText("Save budget").performClick(); saved()
        val date = LocalDate.now(ZoneId.of("Asia/Jakarta"))
        tag("overall_actual")
        compose.onNodeWithTag("overall_actual").assertTextEquals("Recorded expenses: " + MoneyText.format(Money(2500000)))
        tag("overall_remaining")
        compose.onNodeWithTag("overall_remaining").assertTextEquals("Remaining after recorded expenses: " + MoneyText.format(Money(7500000)))
        tag("category_${foodId}_remaining")
        compose.onNodeWithTag("category_${foodId}_remaining").assertTextEquals("Remaining after recorded expenses: " + MoneyText.format(Money(-500000)))
        compose.activityRule.scenario.recreate(); ready()
        tag("overall_actual")
        compose.onNodeWithTag("overall_actual").assertTextEquals("Recorded expenses: " + MoneyText.format(Money(2500000)))
        action("Edit allocation", "budget_overall").performScrollTo().performClick()
        compose.onNodeWithTag("budget_amount").performTextReplacement("10.000")
        compose.onNodeWithText("Save budget").performClick()
        compose.onNodeWithTag("budget_editor_error").assertExists()
        compose.onNodeWithTag("budget_amount").performTextReplacement("150.000")
        compose.onNodeWithText("Save budget").performClick(); saved()
        compose.onNodeWithTag("budget_list").performScrollToIndex(0)
        compose.onNodeWithTag("budget_month").performTextReplacement("2099-02")
        compose.onNodeWithText("Open month").performClick()
        tag("overall_actual")
        compose.onNodeWithTag("overall_actual").assertTextEquals("Recorded expenses: Not available")
        compose.onNodeWithTag("budget_list").performScrollToIndex(0)
        compose.onNodeWithTag("budget_month").performTextReplacement(YearMonth.from(date).toString())
        compose.onNodeWithText("Open month").performClick()
        tag("category_${foodId}_remaining")
        action("Remove", "budget_category_${foodId}").performScrollTo().performClick()
        compose.onNodeWithText("Remove allocation").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Remove allocation?").fetchSemanticsNodes().isEmpty() }
        tag("overall_actual")
        action("Remove", "budget_overall").performScrollTo().performClick()
        compose.onNodeWithText("Remove allocation").performClick()
        compose.onNodeWithTag("budget_list").performScrollToNode(hasText("No budgets yet"))
        compose.onNodeWithText("No budgets yet").assertIsDisplayed()
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(note))
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasAnyChild(hasText(note)))).performClick()
        compose.onNodeWithText("Delete permanently").performClick()
    }
}
