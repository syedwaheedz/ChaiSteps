package com.chaisteps.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaisteps.app.ChaiStepsApp
import com.chaisteps.app.tracking.ActiveWalk
import com.chaisteps.app.ui.components.LocalMapsEnabled
import com.chaisteps.app.ui.theme.ChaiStepsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1400dp-xxhdpi")
class ScreensTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: ChaiStepsApp
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        vm = MainViewModel(app)
    }

    private fun seed() {
        val (data, routes) = SampleData.build()
        routes.forEach { (id, r) -> app.repository.saveRoute(id, r) }
        app.repository.update { data }
    }

    private fun show(tab: Tab = Tab.TODAY, dark: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalMapsEnabled provides false) {
                ChaiStepsTheme(dark = dark) { ChaiStepsRoot(vm, startTab = tab) }
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    @Test fun onboardingCreatesProfileAndFirstWeighIn() {
        show()
        compose.onNodeWithText("Chai & Steps").assertIsDisplayed()
        shot("01_onboarding")
        compose.onNodeWithText("Your name (optional)").performTextInput("Sara")
        compose.onNodeWithText("Current weight").performTextInput("88.5")
        compose.onNodeWithText("Goal weight").performTextInput("75")
        compose.onNodeWithText("Height").performTextInput("165")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.waitForIdle()
        val d = app.repository.data.value
        assertTrue(d.profile.onboarded)
        assertEquals("Sara", d.profile.name)
        assertEquals(88.5, d.weights.single().kg, 0.001)
        assertEquals(75.0, d.profile.goalWeightKg!!, 0.001)
        compose.onNodeWithText("Steps").assertIsDisplayed()
    }

    @Test fun onboardingRejectsImpossibleWeight() {
        show()
        compose.onNodeWithText("Current weight").performTextInput("5")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        assertTrue(!app.repository.data.value.profile.onboarded)
    }

    @Test fun todayScreen() {
        seed()
        show()
        compose.onNodeWithText("5,430").assertIsDisplayed()
        compose.onNodeWithText("Start a walk").assertIsDisplayed()
        shot("02_today")
    }

    @Test fun todayQuickAddChai() {
        seed()
        show()
        val before = app.repository.data.value.chai.size
        compose.onNodeWithContentDescription("Add a cup of chai").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(before + 1, app.repository.data.value.chai.size)
    }

    @Test fun addStepsManually() {
        seed()
        show()
        app.repository.addManualSteps(LocalDate.now().toEpochDay(), 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("6,430").assertIsDisplayed()
    }

    @Test fun walkScreen() {
        seed()
        show(Tab.WALK)
        compose.onNodeWithContentDescription("Start walk").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsDisplayed()
        shot("04_walk")
    }

    @Test fun activeWalk() {
        seed()
        val route = SampleData.loop(25.2048, 55.2708, 0.003, 0).segments
        app.walkTracker.publish(ActiveWalk("live", 0, false, 1_234_000, 1_650.0, 2_210, 1.4, 5f, true, route))
        show(Tab.WALK)
        compose.onNodeWithText("WALKING").assertIsDisplayed()
        compose.onNodeWithText("20:34").assertIsDisplayed()
        shot("05_walk_active")
        compose.onNodeWithContentDescription("Finish").performClick()
        compose.onNodeWithText("Finish walk?").assertIsDisplayed()
        app.walkTracker.publish(null)
    }

    @Test fun walkDetailShowsRouteAndStats() {
        seed()
        show(Tab.WALK)
        compose.onAllNodesWithText("Yesterday", substring = true)[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Walk details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Avg pace").assertIsDisplayed()
        compose.waitForIdle()
        shot("06_walk_detail")
        compose.onNodeWithText("Delete walk").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertEquals(7, app.repository.data.value.walks.size)
    }

    @Test fun weightScreenAndLogging() {
        seed()
        show(Tab.WEIGHT)
        compose.onNodeWithText("Journey").assertIsDisplayed()
        compose.onNodeWithText("Progress").assertIsDisplayed()
        shot("07_weight")
        compose.onNodeWithText("Entries").performScrollTo().assertIsDisplayed()
    }

    @Test fun chaiScreenCustomCup() {
        seed()
        show(Tab.CHAI)
        compose.onNodeWithText("Today's cups").assertIsDisplayed()
        shot("09_chai")
        compose.onNodeWithText(" Other").performClick()
        compose.onNodeWithText("Sulaimani").performClick()
        compose.onNodeWithContentDescription("More sugar").performClick()
        shot("10_chai_dialog")
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        val last = app.repository.data.value.chai.maxBy { it.timeMillis }
        assertEquals(com.chaisteps.app.data.ChaiType.SULAIMANI, last.type)
        assertEquals(1.5, last.sugarTsp, 0.0)
    }

    @Test fun statsScreen() {
        seed()
        show(Tab.STATS)
        compose.onNodeWithText("Walking").assertExists()
        shot("11_stats")
        compose.onAllNodesWithText("30 days")[0].performClick()
        compose.waitForIdle()
        shot("12_stats_30")
    }

    @Test fun settingsUnitsSwitch() {
        seed()
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Pounds").performScrollTo().performClick()
        compose.waitForIdle()
        shot("13_settings")
        assertEquals(com.chaisteps.app.data.WeightUnit.LB, app.repository.data.value.profile.weightUnit)
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Weight").performClick()
        compose.onAllNodes(hasText(" lb", substring = true))[0].assertExists()
    }

    @Test fun darkTheme() {
        seed()
        show(dark = true)
        shot("14_today_dark")
        runBlocking { app.repository.flush() }
    }

    @Test fun emptyStateAfterOnboarding() {
        app.repository.updateProfile { it.copy(onboarded = true) }
        show()
        shot("15_today_empty")
        compose.onNodeWithText("Log your weight").assertIsDisplayed()
        tab("Walk").performClick()
        compose.onNodeWithText("No walks yet. Your first one will appear here.").assertIsDisplayed()
        tab("Stats").performClick()
        compose.waitForIdle()
        tab("Chai").performClick()
        compose.onNodeWithText("No chai yet today.").assertIsDisplayed()
    }
}
