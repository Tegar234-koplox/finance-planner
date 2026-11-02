package com.tegar.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.Money
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class ReportsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun ready()=compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun saved()=compose.waitUntil(60_000) {
        val errors=compose.onAllNodesWithTag("editor_error").fetchSemanticsNodes()
        if (errors.isNotEmpty()) throw AssertionError(errors.first().config[SemanticsProperties.Text].toString())
        compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty()
    }
    private fun add(type: String,amount: String,note: String) {
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_add").performClick()
        if (type!="Expense") {
            compose.onNodeWithText("Type: Expense").performClick()
            compose.onNodeWithText(type,useUnmergedTree=true).performClick()
        }
        compose.onNodeWithTag("transaction_amount").performTextInput(amount)
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_note").performImeAction()
        compose.onNodeWithText("Save transaction").performClick(); saved()
        compose.onNodeWithContentDescription("Navigate back").performClick()
    }
    private fun reportTag(tag: String) {
        compose.onNodeWithTag("reports_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun value(tag: String,expected: String) {
        reportTag(tag)
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { text -> text.text }==expected
        } }
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }
    private fun kind(name: String) {
        compose.onNodeWithTag("reports_list").performScrollToIndex(0)
        compose.onNodeWithTag("report_kind_$name").performClick()
    }
    private fun date(text: String) {
        compose.onNodeWithTag("reports_list").performScrollToIndex(0)
        if (compose.onAllNodesWithTag("report_date").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("Choose date").performClick()
        compose.onNodeWithTag("report_date").performScrollTo().performTextReplacement(text)
        compose.onNodeWithText("Open period").performClick()
    }
    private fun delete(note: String) {
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithText("Transactions").performClick()
        compose.onNodeWithTag("records_list").performScrollToNode(hasText(note))
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasAnyChild(hasText(note)))).performScrollTo().performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Navigate back").performClick()
    }
    @Test fun reportsAndLabeledChartsFollowRecordsPeriodsCoverageAndRecreation() {
        ready()
        val suffix=System.nanoTime()
        val income="Report income $suffix"; val expense="Report expense $suffix"; val contribution="Report contribution $suffix"
        add("Income","40.000",income); add("Expense","10.000",expense); add("Investment contribution","5.000",contribution)
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_reports").performClick()
        value("report_income","Income: "+MoneyText.format(Money(4000000)))
        value("report_expenses","Consumption: "+MoneyText.format(Money(1000000)))
        value("report_contributions","Contributions: "+MoneyText.format(Money(500000)))
        value("report_saving","Income minus consumption: "+MoneyText.format(Money(3000000)))
        value("report_saving_rate","Saving rate: 75.00%")
        reportTag("report_chart_toggle"); compose.onNodeWithTag("report_chart_toggle").performClick()
        val today=LocalDate.now(ZoneId.of("Asia/Jakarta"))
        val dayTag="report_bucket_${today.dayOfMonth-1}"
        reportTag(dayTag)
        compose.onNode(hasText("Income: "+MoneyText.format(Money(4000000))) and hasAnyAncestor(hasTestTag(dayTag))).assertExists()
        compose.onNode(hasText("Consumption: "+MoneyText.format(Money(1000000))) and hasAnyAncestor(hasTestTag(dayTag))).assertExists()
        kind("WEEK"); value("report_expenses","Consumption: "+MoneyText.format(Money(1000000)))
        kind("YEAR"); value("report_income","Income: "+MoneyText.format(Money(4000000)))
        val monthTag="report_bucket_${today.monthValue-1}"
        reportTag(monthTag)
        compose.onNode(hasText("Contributions: "+MoneyText.format(Money(500000))) and hasAnyAncestor(hasTestTag(monthTag))).assertExists()
        compose.activityRule.scenario.recreate(); ready()
        value("report_saving","Income minus consumption: "+MoneyText.format(Money(3000000)))
        reportTag("report_chart_toggle"); compose.onNodeWithText("Hide chart").assertIsDisplayed()
        date("2099-02-10")
        value("report_expenses","Consumption: Not available")
        value("report_coverage","Future period")
        reportTag("report_bucket_0")
        compose.onNode(hasText("Not available; no zero bar is drawn.") and hasAnyAncestor(hasTestTag("report_bucket_0"))).assertExists()
        date("2028-02-30")
        reportTag("report_input_error"); compose.onNodeWithTag("report_input_error").assertIsDisplayed()
        value("report_range","2099-01-01 through 2099-12-31")
        compose.onNodeWithTag("reports_list").performScrollToIndex(0)
        compose.onNodeWithText("Today").performClick()
        value("report_income","Income: "+MoneyText.format(Money(4000000)))
        compose.onNodeWithContentDescription("Navigate back").performClick()
        delete(expense); delete(contribution); delete(income)
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.onNodeWithTag("home_reports").performClick()
        value("report_expenses","Consumption: "+MoneyText.format(Money(0)))
        value("report_saving_rate","Saving rate: Not available")
    }
}
