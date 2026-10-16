package com.lumostech.autoclick

import android.view.View
import android.widget.TimePicker
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assert.*

/** Interact with the same native controls in the Compose-hosted editor and recording panel. */
internal object ScheduleEditorUi {
    fun time(hour: Int? = null, minute: Int? = null) {
        onView(withId(R.id.timePicker)).perform(scrollTo(), object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "select a schedule time"
            override fun perform(uiController: UiController, view: View) {
                (view as TimePicker).apply {
                    if (hour != null) this.hour = hour
                    if (minute != null) this.minute = minute
                }
                uiController.loopMainThreadUntilIdle()
            }
        })
    }

    fun assertTime(hour: Int, minute: Int) {
        onView(withId(R.id.timePicker)).check { view, missing ->
            if (missing != null) throw missing
            (view as TimePicker).apply {
                assertTrue(is24HourView)
                assertEquals(hour, this.hour)
                assertEquals(minute, this.minute)
            }
        }
    }

    fun day(day: Int) = onView(allOf(withTagValue(equalTo(day)), isDescendantOfA(withId(R.id.weekdaysPicker))))
    fun toggleDay(day: Int) = day(day).perform(scrollTo(), click())
    fun assertDaySelected(day: Int) = day(day).check(matches(isSelected()))
    fun save() = onView(withId(R.id.confirm)).perform(scrollTo(), click())
    fun cancel() = onView(withId(R.id.cancel)).perform(scrollTo(), click())
    fun assertOpen() = onView(withId(R.id.dialog_title)).check(matches(withText("修改任务时间")))
    fun assertClosed() = onView(withId(R.id.dialog_title)).check(doesNotExist())
    fun assertSaveDisplayed() = onView(withId(R.id.confirm)).perform(scrollTo()).check(matches(isDisplayed()))
    fun assertSaving() {
        onView(withId(R.id.confirm)).perform(scrollTo()).check(matches(allOf(withText("正在保存…"), not(isEnabled()))))
        onView(withId(R.id.cancel)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.timePicker)).check(matches(not(isEnabled())))
    }
    fun assertError(message: String) = onView(withId(R.id.schedule_error)).perform(scrollTo())
        .check(matches(allOf(withText(message), isDisplayed())))
    fun assertNoError() = onView(withId(R.id.schedule_error)).check(matches(not(isDisplayed())))

    fun waitUntilClosed(compose: ComposeTestRule) {
        compose.waitUntil(5_000) {
            try { assertClosed(); true } catch (_: AssertionError) { false }
        }
    }
}
