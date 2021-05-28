package auth

data class AzureConfig (
    val azureClientSecret: String,
    val azureClientId: String,
    val tokenEndpoint: String
)

val azureConfig = AzureConfig(
    System.getenv("AZURE_APP_CLIENT_SECRET"),
    System.getenv("AZURE_APP_CLIENT_ID"),
    System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
)