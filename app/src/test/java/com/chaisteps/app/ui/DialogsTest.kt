package com.chaisteps.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaisteps.app.ChaiStepsApp
import com.chaisteps.app.data.stepDay
import com.chaisteps.app.ui.components.LocalMapsEnabled
import com.chaisteps.app.ui.theme.ChaiStepsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Typing into text fields inside dialogs. Runs on Robolectric's default screen: with custom
 * screen qualifiers Robolectric never idles while a dialog contains a text field (also with a
 * bare Material3 AlertDialog), which is a test-environment limitation, not app behaviour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DialogsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: ChaiStepsApp
    private val today = LocalDate.now().toEpochDay()

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val (data, _) = SampleData.build()
        app.repository.update { data }
        val vm = MainViewModel(app)
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) { ChaiStepsTheme { ChaiStepsRoot(vm) } }
        }
    }

    @Test fun addAndSetSteps() {
        compose.onNodeWithContentDescription("Add or edit steps").performClick()
        compose.onNodeWithText("Steps to add").performTextInput("1000")
        compose.onRoot().captureRoboImage("build/screenshots/03_add_steps.png")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(6_430, app.repository.data.value.stepDay(today).total)

        compose.onNodeWithContentDescription("Add or edit steps").performClick()
        compose.onNodeWithText("Set the total instead").performClick()
        compose.onNodeWithText("Total steps").performTextInput("12000")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(12_000, app.repository.data.value.stepDay(today).total)
    }

    @Test fun logWeight() {
        compose.onNode(hasText("Weight") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.onAllNodesWithText("Log weight", useUnmergedTree = true)[0].performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("85.2")
        compose.onRoot().captureRoboImage("build/screenshots/08_weight_dialog.png")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(85.2, app.repository.data.value.weights.single { it.epochDay == today }.kg, 0.001)
    }

    @Test fun invalidWeightCannotBeSaved() {
        compose.onNode(hasText("Weight") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.onAllNodesWithText("Log weight", useUnmergedTree = true)[0].performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("3")
        val before = app.repository.data.value.weights.size
        compose.onNodeWithText("Save").performClick()
        assertEquals(before, app.repository.data.value.weights.size)
    }
}
