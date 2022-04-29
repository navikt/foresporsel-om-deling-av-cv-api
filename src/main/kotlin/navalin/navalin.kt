package navalin

import io.javalin.Javalin

fun Javalin.configureHealthEndpoints(isAliveFunction: () -> Int = { 200 }) {
    get("/internal/isAlive") { it.status(isAliveFunction()) }
    get("/internal/isReady") { it.status(200) }
}
