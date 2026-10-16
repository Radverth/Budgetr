package com.budgetr.app.ui.screens.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.budgetr.app.data.model.ReviewDestination
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReviewChoiceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `a choice requires explicit confirmation before it is applied`() {
        var calls = 0
        compose.setContent { MaterialTheme { ReviewChoice(5000, emptyList(), emptyList(), false) { destination, amount ->
            assertEquals(ReviewDestination.NextPeriod, destination)
            assertEquals(0L, amount)
            calls++
        } } }
        compose.onNodeWithText("Review choice").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Confirm choice").performClick()
        assertEquals(1, calls)
    }
    @Test fun `saving disables submission`() {
        compose.setContent { MaterialTheme { ReviewChoice(5000, emptyList(), emptyList(), true) { _, _ -> fail("Submitted while saving") } } }
        compose.onNodeWithText("Review choice").assertIsNotEnabled()
    }
    @Test fun `amount above available money cannot submit`() {
        compose.setContent { MaterialTheme { ReviewChoice(5000, listOf("Holiday"), emptyList(), false) { _, _ -> fail("Submitted unavailable money") } } }
        compose.onNodeWithText("Goal: Holiday").performClick()
        compose.onNodeWithText("Amount (£)").performTextReplacement("60")
        compose.onNodeWithText("Review choice").assertIsNotEnabled()
    }
}
