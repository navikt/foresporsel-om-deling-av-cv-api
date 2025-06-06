package utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

val osloTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Oslo")
val osloZoneId: ZoneId = osloTimeZone.toZoneId()

fun nowOslo(): ZonedDateTime = ZonedDateTime.now().atOslo()

fun ZonedDateTime.atOslo(): ZonedDateTime = this.withZoneSameInstant(osloZoneId).truncatedTo(MILLIS)

fun LocalDateTime.atOslo(): ZonedDateTime = this.atZone(osloZoneId).truncatedTo(MILLIS)

fun Instant.atOslo(): ZonedDateTime = this.atZone(osloZoneId).truncatedTo(MILLIS)
