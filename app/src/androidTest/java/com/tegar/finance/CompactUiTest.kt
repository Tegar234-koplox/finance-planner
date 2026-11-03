package com.tegar.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CompactUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun explanationsAreOptionalWhileNumbersAndActionsStayVisibleInBothLanguages() {
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("app_ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_wealth"))
        compose.onNodeWithTag("home_wealth").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("wealth_total").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("wealth_total").assertIsDisplayed()
        compose.onNodeWithTag("wealth_add").assertIsDisplayed()
        val intro = compose.activity.getString(R.string.wealth_intro)
        compose.onAllNodesWithText(intro).assertCountEquals(0)
        compose.onNodeWithTag("wealth_help").performClick()
        compose.onNodeWithText(intro).assertExists()
        compose.onNodeWithTag("wealth_help_close").performClick()
        compose.onAllNodesWithText(intro).assertCountEquals(0)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language_id").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("wealth_help").performClick()
        compose.onNodeWithTag("wealth_help_content").assertExists()
        compose.onNodeWithText("Tentang angka ini").assertIsDisplayed()
        compose.onNodeWithTag("wealth_help_close").performClick()
        compose.onNodeWithTag("wealth_total").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pengaturan").performClick()
        compose.onNodeWithTag("language_en").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
    }
}
