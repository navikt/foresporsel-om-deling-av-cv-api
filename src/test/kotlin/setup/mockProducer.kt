package setup

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer

fun mockProducer(): Producer<String, String> {
    return MockProducer(true, null, null)
}
