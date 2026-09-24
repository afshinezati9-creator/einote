package com.einote.app.util

import java.util.Calendar

object PersianFormat {
    private val persianDigits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun digits(value: Long): String = value.toString().map {
        if (it.isDigit()) persianDigits[it.digitToInt()] else it
    }.joinToString("")

    fun digits(value: Int): String = digits(value.toLong())

    fun commaNumber(value: Long): String {
        val sign = if (value < 0) "−" else ""
        val raw = kotlin.math.abs(value).toString()
        val grouped = raw.reversed().chunked(3).joinToString("٬").reversed()
        return sign + grouped.map { if (it.isDigit()) persianDigits[it.digitToInt()] else it }.joinToString("")
    }

    fun toman(value: Long): String = commaNumber(value) + " تومان"

    fun jalaliDate(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val (jy, jm, jd) = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return digits(jy) + "/" + digits(jm).padStart(2, '۰') + "/" + digits(jd).padStart(2, '۰')
    }

    fun jalaliDateTime(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return jalaliDate(millis) + "، " + digits(c.get(Calendar.HOUR_OF_DAY)) + ":" + digits(c.get(Calendar.MINUTE)).padStart(2, '۰')
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDays = intArrayOf(31, if (isLeap(gy)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDays[i]
        gDayNo += gd2
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        var jDay = jDayNo % 12053
        var jy = 979 + 33 * jNp + 4 * (jDay / 1461)
        jDay %= 1461
        if (jDay >= 366) {
            jy += (jDay - 1) / 365
            jDay = (jDay - 1) % 365
        }
        return if (jDay < 186) {
            Triple(jy, 1 + jDay / 31, 1 + jDay % 31)
        } else {
            Triple(jy, 7 + (jDay - 186) / 30, 1 + (jDay - 186) % 30)
        }
    }

    private fun isLeap(year: Int): Boolean =
        year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
}
