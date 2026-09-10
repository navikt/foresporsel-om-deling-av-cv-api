FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
ENV TZ="Europe/Oslo"
COPY ./build/install/foresporsel-om-deling-av-cv-api/lib ./lib
EXPOSE 8333
CMD ["-cp", "lib/*", "AppKt"]
