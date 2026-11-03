package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class BookkeepingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) {
        compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty()
    }
    private fun show(text: String) {
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun action(label: String, record: String) = compose.onNode(
        hasText(label) and hasAnyAncestor(hasAnyChild(hasText(record))))
    private fun waitForAction(label: String, record: String) = compose.waitUntil(60_000) {
        compose.onAllNodes(hasText(label) and isEnabled() and hasAnyAncestor(hasAnyChild(hasText(record))))
            .fetchSemanticsNodes().isNotEmpty()
    }
    private fun transactionSaved() = compose.waitUntil(60_000) {
        val errors = compose.onAllNodesWithTag("editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError("Save failed: " + errors.first().config[SemanticsProperties.Text])
        compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty()
    }
    @Test fun createEditFilterRecreateAndDeleteTransaction() {
        ready()
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("add_record").performClick()
        compose.onNodeWithText("Type: Expense").performClick()
        compose.onNodeWithText("Income", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Category: Salary").assertExists()
        compose.onNodeWithTag("transaction_amount").performTextInput("25.000,50")
        val note = "UI transaction ${System.nanoTime()}"
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick()
        transactionSaved()
        show(note)
        action("Edit", note).performClick()
        compose.onNodeWithTag("transaction_amount").performTextReplacement("30.000,75")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextReplacement("$note edited")
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick()
        transactionSaved()
        compose.activityRule.scenario.recreate()
        ready()
        show("$note edited")
        compose.onNodeWithTag("records_list").performScrollToIndex(0)
        compose.onNodeWithText("Show filters").performClick()
        val tomorrow = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(1).toString()
        compose.onNodeWithTag("filter_from").performScrollTo().performTextInput(tomorrow)
        compose.onNodeWithText("Apply filters").performScrollTo().performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("history_count"))
        compose.onNodeWithText("No transactions match these filters.").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performScrollTo().performClick()
        compose.onNodeWithText("Hide filters").performScrollTo().performClick()
        show("$note edited")
        action("Delete", "$note edited").performClick()
        compose.onNodeWithText("Cancel").performClick()
        show("$note edited")
        action("Delete", "$note edited").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("$note edited").fetchSemanticsNodes().isEmpty() }
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithText("$note edited").assertDoesNotExist()
    }
    @Test fun categoryCanBeCreatedRenamedArchivedRestoredAndDeleted() {
        ready()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithTag("add_record").performClick()
        val name = "UI category ${System.nanoTime()}"
        compose.onNodeWithTag("category_name").performTextInput(name)
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isEmpty() }
        show(name)
        action("Rename", name).performClick()
        compose.onNodeWithTag("category_name").performTextReplacement("$name renamed")
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isEmpty() }
        show("$name renamed")
        action("Archive", "$name renamed").performClick()
        waitForAction("Restore", "$name renamed")
        action("Restore", "$name renamed").assertExists().performClick()
        waitForAction("Archive", "$name renamed")
        compose.activityRule.scenario.recreate()
        ready()
        show("$name renamed")
        action("Archive", "$name renamed").assertExists()
        action("Delete", "$name renamed").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("$name renamed").fetchSemanticsNodes().isEmpty() }
    }
}
