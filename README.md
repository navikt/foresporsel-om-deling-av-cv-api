# foresporsel-om-deling-av-cv-api

Mottar API-kall fra rekrutteringsbistand-kandidat som fører til sending av forespørsel om deling av brukers CV på Kafka til Aktivitetsplanen. Mottar kvittering på Kafka om at dette har gått bra.
Bruker kan godta eller avslå at deres CV kan deles med arbeidsgiver. Svaret på forespørselen mottas også på Kafka.


# Manuell testing av applikasjon i Rekrutteringsbistand
- Del stilling med arbeidstaker
- Trykk på “info” på kandidatraden og logg inn på aktivitetsplanen og svar på forespørselen
- Svaret skal snart bli synlig i Rekrutteringsbistand
- Del CV-en med arbeidsgiver, Aktivitetsplanen skal vise ny etikett på stillingskortet
- Marker at noen andre fikk jobben
- Lukk kandidatlista
- Sjekk at aktivitetsplanen til han/hun med delt CV og som ikke fikk jobben får en “IKKE FÅTT JOBBEN”-etikett


# Henvendelser

## For Nav-ansatte

* Dette Git-repositoriet eies
  av [team Toi i produktområde Arbeidsgiver](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack-kanaler:
  * [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)
  * [#rekrutteringsbistand-værsågod](https://nav-it.slack.com/archives/C02HWV01P54)

## For folk utenfor Nav

IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
