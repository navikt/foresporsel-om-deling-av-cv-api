import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import setup.TestDatabase
import setup.mockProducerJson
import java.time.LocalDateTime
import java.util.*

class KandidatEventTest {

    @Test
    fun håndterEvent() {

        val database = TestDatabase()

        val testRapid = TestRapid()
        val mockProducer = mockProducerJson

        val stillingsId = UUID.randomUUID()
        val aktørId = "12345123451"
        val forespørselId = UUID.randomUUID()

        database.lagreBatch(
            listOf(
                enForespørsel(
                    aktørId,
                    deltStatus = DeltStatus.IKKE_SENDT,
                    stillingsId = stillingsId,
                    forespørselId = forespørselId
                ),
            )
        )


        startLokalApp(database = database, testRapid = testRapid, jsonProducer = mockProducer).apply {
            val eventJson = """
            {"@event_name":"kandidat.dummy.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand","kandidathendelse":{"type":"CV_DELT_VIA_REKRUTTERINGSBISTAND","aktørId":"$aktørId","organisasjonsnummer":"913086619","kandidatlisteId":"8081ef01-b023-4cd8-bd87-b830d9bcf9a4","tidspunkt":"2022-07-26T10:37:47.074+02:00"}}
        """.trimIndent()

            testRapid.publish(eventJson)

            val rapidInspektør = testRapid.inspektør
            assertThat(rapidInspektør.size).isEqualTo(1)
            val history = mockProducer.history()
            assertThat(history).hasSize(1)
            assertThat(history.first().key()).isEqualTo(forespørselId)
            assertThat(
                history.first().value()
            ).isEqualTo("""{"type":"CV_DELT","detaljer":"","tidspunkt":${LocalDateTime.now()}}""")

        }
    }
}
