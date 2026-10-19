package com.lawyerup.app
import com.lawyerup.app.data.DeadlineCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
class DeadlineCalculatorTest { @Test fun countsFromNextDay(){assertEquals("2024-01-04",DeadlineCalculator.calculate("2024-01-01",3))} @Test fun leapYear(){assertEquals("2024-03-01",DeadlineCalculator.calculate("2024-02-28",2))} }