import com.github.kittinunf.fuel.Fuel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LivenessTest {

    @Test
    fun `Liveness skal returnere 200 hvis appen er oppe`() {
        startLokalApp().use {
            val (_, response, _) = Fuel.get("http://localhost:8333/internal/isAlive").response()
            assertThat(response.statusCode).isEqualTo(200)
        }
    }

    @Test
    fun `Readiness skal returnere 200 hvis appen er oppe`() {
        startLokalApp().use {
            val (_, response, _) = Fuel.get("http://localhost:8333/internal/isReady").response()
            assertThat(response.statusCode).isEqualTo(200)
        }
    }
}
