FROM navikt/java:17
COPY ./build/libs/foresporsel-om-deling-av-cv-api-all.jar app.jar

EXPOSE 8333
