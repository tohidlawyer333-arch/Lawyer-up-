package com.lawyerup.app.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.lawyerup.app.ui.PersianDates
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PersianDatesTest {
    @Test fun nowruzRoundTrip() {val date=LocalDate.of(2025,3,21);assertEquals("1404/01/01",PersianDates.display(date));assertEquals(date,PersianDates.parse("۱۴۰۴/۰۱/۰۱"))}
    @Test fun sixMonthsAreSolarNotGregorianApproximation() {val start=PersianDates.parse("1404/01/01");assertEquals("1404/07/01",PersianDates.display(PersianDates.months(start,6)))}
    @Test fun invalidDateFails() {assertTrue(runCatching {PersianDates.parse("1404/13/01")}.isFailure)}
}
