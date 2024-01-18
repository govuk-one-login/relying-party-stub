FROM gradle:8.5.0-jdk17@sha256:7020357eb6032236390ef34a8903fbcb4dc2bf29e81d89bd93bdaa844e098518 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
RUN gradle clean build --no-daemon

ENTRYPOINT ["gradle", "run"]
