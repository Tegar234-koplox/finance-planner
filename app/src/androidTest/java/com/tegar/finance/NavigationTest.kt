package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun fourDestinationsAndSettingsBackNavigation() {
        ready()
        compose.onNodeWithText("No activity yet").assertIsDisplayed()
        compose.onNodeWithTag("nav_budget").performClick()
        compose.onNodeWithText("No budgets yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("No budgets yet").assertIsDisplayed()
        compose.onNodeWithTag("nav_plan").performClick()
        compose.onNodeWithText("No goals yet").assertIsDisplayed()
        compose.onNodeWithTag("nav_market").performClick()
        compose.onNodeWithText("Market monitoring is not enabled").assertIsDisplayed()
    }
    @Test fun navigationSurvivesActivityRecreation() {
        ready()
        compose.onNodeWithTag("nav_plan").performClick()
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithText("No goals yet").assertIsDisplayed()
    }
}
