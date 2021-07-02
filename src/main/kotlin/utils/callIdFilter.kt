package utils

import io.javalin.http.Context
import java.util.*

const val foretrukkenCallIdHeaderKey = "Nav-Call-Id"

val settCallId: (Context) -> Unit = {
    val foretrukkenCallId = it.header(foretrukkenCallIdHeaderKey)
    val alternativCallId = it.header("Nav-CallId")
    val sisteCallId = it.header("X-Nav-CallId")

    val callId = foretrukkenCallId ?: alternativCallId ?: sisteCallId ?: UUID.randomUUID().toString()
    it.attribute(foretrukkenCallIdHeaderKey, callId)
}

fun Context.hentCallId(): String {
    return attribute(foretrukkenCallIdHeaderKey)!!
}
