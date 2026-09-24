package com.einote.app.util

import java.util.Calendar

object PersianFormat {
    private val persianDigits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun digits(value: Long): String = value.toString().map {
        if (it.isDigit()) persianDigits[it.digitToInt()] else it
    }.joinToString("")

    fun digits(value: Int): String = digits(value.toLong())

    fun latinDigits(value: String): String =
        value.map { ch -> if (ch in '۰'..'۹') ('0'.code + (ch - '۰')).toChar() else ch }.joinToString("")

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

    fun currentJalali(): Triple<Int, Int, Int> {
        val c = Calendar.getInstance()
        return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun jalaliToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val g = jalaliToGregorian(year, month, day)
        return Calendar.getInstance().apply {
            set(g.first, g.second - 1, g.third, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDays = intArrayOf(31, if (isLeap(gy)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy2 = gy - 1600
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
        return if (jDay < 186) Triple(jy, 1 + jDay / 31, 1 + jDay % 31)
        else Triple(jy, 7 + (jDay - 186) / 30, 1 + (jDay - 186) % 30)
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var y = jy - 979
        var dayNo = 365 * y + (y / 33) * 8 + ((y % 33) + 3) / 4
        dayNo += if (jm <= 6) (jm - 1) * 31 else (jm - 1) * 30 + 6
        dayNo += jd - 1
        var gDayNo = dayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++
            else leap = false
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        var gm = 0
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        while (gDayNo >= days[gm]) {
            gDayNo -= days[gm]
            gm++
        }
        return Triple(gy, gm + 1, gDayNo + 1)
    }

    private fun isLeap(year: Int): Boolean =
        year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
}
