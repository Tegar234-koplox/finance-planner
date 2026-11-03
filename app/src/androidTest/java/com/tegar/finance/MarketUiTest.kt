package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MarketUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() = compose.waitUntil(60_000) {
        compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun marketPortfolioNavigationLanguageAndActivityRestartWorkWithoutAProvider() {
        ready(); compose.onNodeWithTag("nav_market").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("market_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No market prices yet").assertIsDisplayed()
        compose.onNodeWithTag("market_portfolio").performClick()
        compose.onNodeWithText("Portfolio incl. cash").assertExists()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("market_empty").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language_id").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Belum ada harga pasar").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); ready()
        compose.onNodeWithText("Belum ada harga pasar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pengaturan").performClick()
        compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
    }
}
