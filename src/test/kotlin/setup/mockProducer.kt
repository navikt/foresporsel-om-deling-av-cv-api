package setup

import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.serialization.StringSerializer


val dummyForesporselOmDelingAvCvSerializer = { _: String, _: ForesporselOmDelingAvCv -> ByteArray(0) }

val mockProducerAvro = MockProducer(true, StringSerializer(), dummyForesporselOmDelingAvCvSerializer)

val mockProducerJson = MockProducer(Cluster.empty(), false, null, StringSerializer(), StringSerializer())

fun mockProducerUtenAutocomplete(): MockProducer<String, ForesporselOmDelingAvCv> =
    MockProducer(false, StringSerializer(), dummyForesporselOmDelingAvCvSerializer)
