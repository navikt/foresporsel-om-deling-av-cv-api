package utils

import io.javalin.http.Context
import java.util.*

const val foretrukkenCallIdHeaderKey = "Nav-Call-Id"

val settCallId: (Context) -> Unit = {
    val callId = it.header(foretrukkenCallIdHeaderKey) ?: it.header("Nav-CallId") ?: it.header("X-Nav-CallId") ?: UUID.randomUUID().toString()
    it.header(foretrukkenCallIdHeaderKey, callId)
}

fun Context.hentCallId() = UUID.fromString(header(foretrukkenCallIdHeaderKey)!!)
