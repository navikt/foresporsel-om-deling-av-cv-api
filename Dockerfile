FROM ghcr.io/navikt/baseimages/temurin:17
COPY ./build/libs/foresporsel-om-deling-av-cv-api-all.jar app.jar

EXPOSE 8333
