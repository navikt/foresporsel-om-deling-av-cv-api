package setup

import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.producer.MockProducer

val mockProducerAvro = MockProducer<String, ForesporselOmDelingAvCv>(true, null, null)

val mockProducerJson = MockProducer<String, String>()

fun mockProducerUtenAutocomplete(): MockProducer<String, ForesporselOmDelingAvCv> =
    MockProducer(false, null, null)
