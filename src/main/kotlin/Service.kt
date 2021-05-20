import io.javalin.http.Context
import utils.log

class Service {

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        log.info("lagre forespørsel")
    }
}

