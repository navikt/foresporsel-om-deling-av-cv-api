package utils

import io.javalin.http.Context
import java.util.*

const val foretrukkenCallIdHeaderKey = "Nav-Call-Id"

val settCallId: (Context) -> Unit = {
    val foretrukkenCallId = it.header(foretrukkenCallIdHeaderKey)
    val alternativCallId = it.header("Nav-CallId")
    val sisteCallId = it.header("X-Nav-CallId")

    log("settCallId").info("foretrukkenCallId: $foretrukkenCallId, alternativ: $alternativCallId, siste: $sisteCallId")

    val callId = foretrukkenCallId ?: alternativCallId ?: sisteCallId ?: UUID.randomUUID().toString()
    it.attribute(foretrukkenCallIdHeaderKey, callId)
}

fun Context.hentCallId(): UUID {
    val callId: String? = attribute(foretrukkenCallIdHeaderKey)
    log("hentCallId").info("Hentet callId: $callId")

    return UUID.fromString(callId!!)
}
