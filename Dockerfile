FROM gcr.io/distroless/java21:nonroot

USER root
RUN mkdir -p /home/nonroot/
USER nonroot

ADD build/distributions/foresporsel-om-deling-av-cv-api.tar /

# Asume that logback.xml is located in the project/app root dir.
# The unconventional location is a signal to developers to make them aware that we use this file in an unconventional
# way in the ENTRYPOINT command in this Dockerfile.
COPY logback.xml /

# Set logback.xml explicitly, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "/foresporsel-om-deling-av-cv-api/lib/*", "AppKt"]

EXPOSE 8333
