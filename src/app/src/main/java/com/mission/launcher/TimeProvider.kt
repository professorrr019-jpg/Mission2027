package com.mission.launcher

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The only approved source of wall-clock time and IST date/time conversions. */
object TimeProvider {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun now(): LocalDateTime = Instant.now().atZone(zone).toLocalDateTime()

    fun today(): LocalDate = now().toLocalDate()

    fun time(): LocalTime = now().toLocalTime()

    fun millis(): Long = System.currentTimeMillis()

    /** Next refresh boundary for the chosen wallpaper interval (15, 30, 60... minutes), aligned to the IST clock. */
    fun nextInterval(nowMillis: Long = millis()): Long {
        val iv = Store.i("wp_interval", 30).coerceIn(15, 240) * 60000L
        val off = zone.rules.getOffset(Instant.ofEpochMilli(nowMillis)).totalSeconds * 1000L
        return ((nowMillis + off) / iv + 1) * iv - off
    }

    fun nextHalfHour(nowMillis: Long = millis()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        val minute = if (now.minute < 30) 30 else 0
        var next = now.withSecond(0).withNano(0).withMinute(minute)
        if (next <= now) next = next.plusHours(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    fun halfHourSlots(
        start: LocalTime,
        end: LocalTime,
    ): List<LocalTime> {
        val first = start.withSecond(0).withNano(0)
        val last = end.withSecond(0).withNano(0)
        val out = ArrayList<LocalTime>()
        var slot = first
        while (slot <= last && out.size < 96) {
            out += slot
            slot = slot.plusMinutes(30)
        }
        return out
    }
}
