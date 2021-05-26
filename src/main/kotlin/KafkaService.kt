class KafkaService(private val repository: Repository) {

    fun sendUsendteForespørsler() {
        repository.hentUsendteForespørsler().forEach {
            // TODO: Send forespørsel på Kafka

            repository.markerForespørselSendt(it.id)
        }
    }
}