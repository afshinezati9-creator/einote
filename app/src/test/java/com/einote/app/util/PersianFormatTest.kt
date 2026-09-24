package com.einote.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianFormatTest {
    @Test fun latinDigits_areConverted() {
        assertEquals("123456", PersianFormat.latinDigits("۱۲۳۴۵۶"))
    }

    @Test fun commaNumber_usesPersianDigits() {
        assertEquals("۱٬۲۳۴٬۵۶۷", PersianFormat.commaNumber(1234567))
    }

    @Test fun jalali_roundTrip_isStable() {
        val millis = PersianFormat.jalaliToMillis(1405, 7, 2, 12, 30)
        assertEquals("۱۴۰۵/۰۷/۰۲", PersianFormat.jalaliDate(millis))
    }
}
