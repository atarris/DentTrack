package com.stocktrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class SeasonsTest {

    @Test
    fun eachHolidayIsIncludedOnItsDay() {
        assertEquals(Season.MARCH_11, Season.on(3, 11))
        assertEquals(Season.HALLOWEEN, Season.on(10, 30))
        assertEquals(Season.CHRISTMAS, Season.on(12, 24))
        assertEquals(Season.CHRISTMAS, Season.on(12, 25))
        assertEquals(Season.NEW_YEAR, Season.on(12, 31))
        assertEquals(Season.NEW_YEAR, Season.on(1, 1))
        assertEquals(Season.VALENTINE, Season.on(2, 14))
        assertEquals(Season.INDEPENDENCE, Season.on(2, 16))
        assertEquals(Season.JONINES, Season.on(6, 23))
        assertEquals(Season.JONINES, Season.on(6, 24))
        assertEquals(Season.MINDAUGINES, Season.on(7, 6))
    }

    @Test
    fun theBirthdayIsOctober31stToNovember2nd() {
        assertEquals(Season.HALLOWEEN, Season.on(10, 30))
        assertNull(Season.on(11, 3))
        assertEquals(Season.BIRTHDAY, Season.on(10, 31))
        assertEquals(Season.BIRTHDAY, Season.on(11, 1))
        assertEquals(Season.BIRTHDAY, Season.on(11, 2))
    }

    @Test
    fun ordinaryDaysHaveNoTheme() {
        assertNull(Season.on(1, 2))
        assertNull(Season.on(2, 7))
        assertNull(Season.on(2, 22))
        assertNull(Season.on(3, 4))
        assertNull(Season.on(3, 12))
        assertNull(Season.on(6, 17))
        assertNull(Season.on(6, 25))
        assertNull(Season.on(7, 7))
        assertNull(Season.on(10, 23))
        assertNull(Season.on(12, 18))
    }

    @Test
    fun everyThemeLastsAWeekAndTheBirthdayThreeDaysAcrossALeapYear() {
        val days = HashMap<Season, Int>()
        val date = GregorianCalendar(2028, Calendar.JANUARY, 1)
        while (date.get(Calendar.YEAR) == 2028) {
            Season.on(date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
                ?.let { days[it] = (days[it] ?: 0) + 1 }
            date.add(Calendar.DAY_OF_MONTH, 1)
        }
        Season.entries.forEach { season ->
            assertEquals(season.name, if (season == Season.BIRTHDAY) 3 else 7, days[season])
        }
    }

    @Test
    fun theRandomSchemeUsesAllColorsAndNeverRepeatsOnTheNextDay() {
        val days = (20_000L until 21_000L).map { GuiScheme.ofDay(it) }
        assertEquals(GuiScheme.entries.toSet(), days.toSet())
        days.zipWithNext().forEach { (today, tomorrow) -> assertTrue(today != tomorrow) }
        assertEquals(GuiScheme.ofDay(20_500L), GuiScheme.ofDay(20_500L))
    }

    @Test
    fun theNewYearGreetingNamesTheYearThatIsStarting() {
        assertEquals("Sėkmingų 2027-ųjų!", Season.NEW_YEAR.subtitle(2026, 12))
        assertEquals("Sėkmingų 2027-ųjų!", Season.NEW_YEAR.subtitle(2027, 1))
        assertEquals("Sėkmingų 2027-ųjų!", Season.NEW_YEAR.subtitle(2026, 11)) // the preview in Settings
        assertEquals("Daug laimės, sveikatos ir šypsenų!", Season.BIRTHDAY.subtitle(2026, 11))
    }
}
