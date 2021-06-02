package setup

import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCv
import org.apache.kafka.clients.producer.MockProducer

fun mockProducer(): MockProducer<String, ForesporselOmDelingAvCv> {
    return MockProducer(true, null, null)
}

fun mockProducerUtenAutocomplete(): MockProducer<String, ForesporselOmDelingAvCv> =
    MockProducer(false, null, null)