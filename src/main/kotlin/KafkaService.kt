class KafkaService(private val repository: Repository) {

    fun sendUsendteForespørsler() {

        val usendteForespørsler = repository.hentUsendteForespørsler()

        // TODO: Send usendte forespørsler på Kafka
        // TODO: Oppdater delt-status i databasen
    }
}