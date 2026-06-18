package utils

enum class Miljø {
    DEV_FSS, PROD_FSS, LOKAL;

    companion object {
        val current: Miljø = when (val c = System.getenv("NAIS_CLUSTER_NAME")) {
            "dev-fss" -> DEV_FSS
            "prod-fss" -> PROD_FSS
            null -> LOKAL
            else -> throw RuntimeException("Ukjent cluster: $c")
        }
    }

    fun asString(): String = name.lowercase().replace("_", "-")
}
