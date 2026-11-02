package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ForecastUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    private fun scroll(tag: String) = compose.onNodeWithTag("forecast_list").performScrollToNode(hasTestTag(tag))
    @Test fun planShowsHonestUnavailableHistoryAndRestoresMethodWindowAcrossRecreation() {
        ready(); compose.onNodeWithTag("nav_plan").performClick()
        scroll("forecast_evidence"); compose.onNodeWithTag("forecast_evidence").assertTextEquals("Evidence: UNAVAILABLE")
        scroll("forecast_1_unavailable"); compose.onNodeWithTag("forecast_1_unavailable").assertTextContains("no complete covered months", substring = true)
        scroll("forecast_3_unavailable"); compose.onNodeWithTag("forecast_3_unavailable").assertTextContains("no complete covered months", substring = true)
        compose.onNodeWithTag("forecast_list").performScrollToIndex(0)
        compose.onNodeWithTag("forecast_method_WEIGHTED").performClick()
        compose.onNodeWithTag("forecast_window_12").performClick()
        compose.activityRule.scenario.recreate(); ready()
        compose.onNodeWithTag("forecast_list").performScrollToIndex(0)
        compose.onNodeWithTag("forecast_method_WEIGHTED").assertIsSelected()
        compose.onNodeWithTag("forecast_window_12").assertIsSelected()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("forecast_method_WEIGHTED").assertIsSelected()
        scroll("forecast_future_scope")
        compose.onNodeWithTag("forecast_future_scope").assertTextContains("Goals and purchases are available", substring = true)
    }
}
