FROM navikt/java:15
COPY ./build/libs/foresporsel-om-deling-av-cv-api-all.jar app.jar

EXPOSE 8333
