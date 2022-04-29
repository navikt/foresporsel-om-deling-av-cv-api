package navalin

import io.javalin.Javalin
import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import no.nav.security.token.support.core.configuration.IssuerProperties

fun Javalin.configureHealthEndpoints(isAliveFunction: () -> Int = { 200 }) {
    get("/internal/isAlive") { it.status(isAliveFunction()) }
    get("/internal/isReady") { it.status(200) }
}

data class RoleConfig(
    val role: RouteRole,
    val issuerProperties: List<IssuerProperties>,
    val necessaryTokenClaims: List<String>
)

fun accessManager(roleConfigs: List<RoleConfig>): AccessManager {

}