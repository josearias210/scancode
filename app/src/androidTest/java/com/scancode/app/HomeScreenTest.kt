package com.scancode.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @Test
    fun showsTheHomeScreenFromTheLauncher() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
            "ScanCode must have a launcher activity"
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use {
            runCatching {
                onView(withId(R.id.scanTitle)).check(matches(isDisplayed()))
                onView(withId(R.id.historyButton)).check(matches(isDisplayed()))
            }.onFailure { assertionFailure ->
                runCatching { saveHomeScreen() }.onFailure(assertionFailure::addSuppressed)
            }.getOrThrow()

            saveHomeScreen()
        }
    }

    private fun saveHomeScreen() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val screenshot = checkNotNull(automation.takeScreenshot()) { "Android could not capture the screen" }
        screenshot.writeToTestStorage("home-screen")
    }
}
