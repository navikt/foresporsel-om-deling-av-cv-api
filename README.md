# foresporsel-om-deling-av-cv-api

Mottar API-kall fra rekrutteringsbistand-kandidat som fører til sending av forespørsel om deling av brukers CV på Kafka til Aktivitetsplanen. Mottar kvittering på Kafka om at dette har gått bra.
Bruker kan godta eller avslå at deres CV kan deles med arbeidsgiver. Svaret på forespørselen mottas også på Kafka.

# Henvendelser

## For Nav-ansatte

- Dette Git-repositoriet eies av [Team inkludering i Produktområde arbeidsgiver](https://navno.sharepoint.com/sites/intranett-prosjekter-og-utvikling/SitePages/Produktomr%C3%A5de-arbeidsgiver.aspx).
- Slack-kanaler:
  - [#inkludering-utvikling](https://nav-it.slack.com/archives/CQZU35J6A)
  - [#arbeidsgiver-utvikling](https://nav-it.slack.com/archives/CD4MES6BB)
  - [#arbeidsgiver-general](https://nav-it.slack.com/archives/CCM649PDH)

## For folk utenfor Nav

- Opprett gjerne en issue i Github for alle typer spørsmål
- IT-utviklerne i Github-teamet https://github.com/orgs/navikt/teams/arbeidsgiver-inkludering
- IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)

# For testing av applikasjon i Rekrutteringsbistand
- Del stilling med arbeidstaker
- Trykk på “info” på kandidatraden og logg inn på aktivitetsplanen og svar på forespørselen
- Svaret skal snart bli synlig i Rekrutteringsbistand
- Del CV-en med arbeidsgiver, Aktivitetsplanen skal vise ny etikett på stillingskortet
- Marker at noen andre fikk jobben
- Lukk kandidatlista
- Sjekk at aktivitetsplanen til han/hun med delt CV og som ikke fikk jobben får en “IKKE FÅTT JOBBEN”-etikett
- 