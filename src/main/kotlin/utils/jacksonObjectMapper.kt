package utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.ZoneId
import java.util.*

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .setTimeZone(TimeZone.getTimeZone(ZoneId.of("Europe/Oslo")))
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
