FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/foresporsel-om-deling-av-cv-api.tar /
ENTRYPOINT ["java", "-cp", "/foresporsel-om-deling-av-cv-api/lib/*", "AppKt"]
EXPOSE 8333
