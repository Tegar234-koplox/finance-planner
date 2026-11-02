package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class GoalsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(60_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun homeValue(tag: String, text: String) {
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { item -> item.text } == text
        } }
    }
    private fun saved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("goal_editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        compose.onAllNodesWithTag("goal_name").fetchSemanticsNodes().isEmpty()
    }
    private fun goalAction(name: String, action: String) {
        compose.onNodeWithTag("goals_list").performScrollToNode(hasText(name))
        compose.onNode(hasText(action) and hasAnyAncestor(hasAnyChild(hasText(name)))).performScrollTo().performClick()
    }
    @Test fun createEditPauseOverfundRecreateTranslateAndDeleteNeverMovesRecordedCash() {
        ready()
        val suffix = System.nanoTime(); val note = "Goal cash $suffix"; val name = "Phone / Ponsel $suffix"
        compose.onNodeWithTag("home_add").performClick()
        compose.onNodeWithText("Type: Expense").performClick(); compose.onNodeWithText("Income", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction_amount").performTextInput("100.000")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction(); compose.onNodeWithText("Save transaction").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("nav_plan").performClick()
        compose.onNodeWithTag("forecast_list").performScrollToNode(hasTestTag("open_goals"))
        compose.onNodeWithTag("open_goals").performScrollTo().performClick()
        compose.onNodeWithTag("goal_add").performClick()
        compose.onNodeWithTag("goal_name").performTextInput(name)
        compose.onNodeWithTag("goal_kind").performScrollTo().performClick(); compose.onNodeWithText("Purchase", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("goal_target").performScrollTo().performTextInput("80.000")
        compose.onNodeWithTag("goal_earmarked").performScrollTo().performTextReplacement("40.000")
        val deadline = YearMonth.from(LocalDate.now(ZoneId.of("Asia/Jakarta"))).plusMonths(3).atEndOfMonth().toString()
        compose.onNodeWithTag("goal_target_date").performScrollTo().performTextReplacement(deadline)
        compose.onNodeWithTag("goal_target_date").performImeAction()
        compose.onNodeWithTag("goal_priority").performScrollTo().performClick(); compose.onNodeWithText("High", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("goal_save").performClick(); saved()
        compose.onNodeWithTag("goals_list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertExists()
        compose.activityRule.scenario.recreate(); ready()
        compose.onNodeWithTag("goals_list").performScrollToNode(hasText(name)); compose.onNodeWithText(name).assertExists()
        goalAction(name, "Edit goal")
        compose.onNodeWithTag("goal_status").performScrollTo().performClick(); compose.onNodeWithText("Paused", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("goal_save").performClick(); saved()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("nav_home").performClick()
        homeValue("home_cash", MoneyText.format(Money(10000000)))
        homeValue("home_goal_reserved", "Reserved for goals: " + MoneyText.format(Money(4000000)))
        homeValue("home_unreserved_cash", "Cash after goal reserves: " + MoneyText.format(Money(6000000)))
        compose.onNodeWithTag("home_goals").performScrollTo().performClick()
        goalAction(name, "Edit goal")
        compose.onNodeWithTag("goal_earmarked").performScrollTo().performTextReplacement("90.000")
        compose.onNodeWithTag("goal_target_date").performScrollTo().performImeAction()
        compose.onNodeWithTag("goal_save").performClick(); saved()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language_id").performScrollTo().performClick(); waitText("Pengaturan")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("goals_list").performScrollToIndex(0)
        compose.onNodeWithText("Tujuan dan pembelian").assertIsDisplayed()
        compose.onNodeWithTag("goals_list").performScrollToNode(hasText(name)); compose.onNodeWithText(name).assertExists()
        compose.onNodeWithContentDescription("Pengaturan").performClick()
        compose.onNodeWithTag("language_en").performScrollTo().performClick(); waitText("Settings")
        compose.onNodeWithContentDescription("Navigate back").performClick()
        goalAction(name, "Delete goal"); compose.onNodeWithTag("goal_delete_confirm").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        homeValue("home_cash", MoneyText.format(Money(10000000)))
        homeValue("home_goal_reserved", "Reserved for goals: " + MoneyText.format(Money(0)))
        compose.onNodeWithTag("home_list").performScrollToIndex(0); compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(note))
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasAnyChild(hasText(note)))).performScrollTo().performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        homeValue("home_cash", MoneyText.format(Money(0)))
    }
}
