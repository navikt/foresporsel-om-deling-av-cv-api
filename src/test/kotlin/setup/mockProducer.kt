package setup

import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import org.apache.kafka.clients.producer.MockProducer

fun mockProducer(): MockProducer<String, ForesporselOmDelingAvCvKafkamelding> {
    return MockProducer(true, null, null)
}
