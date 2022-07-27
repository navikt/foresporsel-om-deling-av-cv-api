package kandidatevent

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import utils.log

class KandidatLytter(private val rapidsConnection: RapidsConnection) : River.PacketListener {
    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Mottok kandidatevent: $JsonMessage")
    }

}
