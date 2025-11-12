FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
ENV TZ="Europe/Oslo"
COPY ./build/libs/foresporsel-om-deling-av-cv-api-all.jar app.jar
EXPOSE 8333
CMD ["-jar", "app.jar"]
