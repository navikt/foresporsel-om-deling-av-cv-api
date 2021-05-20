import com.github.kittinunf.fuel.Fuel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenValideringTest {

    @Test
    fun `Sikrede endepunkter skal returnere 401 hvis requesten ikke inneholder token`() {
        startLokalApp().use {
            val (_, response, _) = Fuel.get("http://localhost:8333/foresporsler").response()
            assertThat(response.statusCode).isEqualTo(401)
        }
    }
}