package com.alarmx.smartalarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class HomeLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsTitleAndAddButton() {
        rule.onNodeWithText("AlarmX").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add alarm").assertIsDisplayed()
    }
}
