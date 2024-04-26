FROM gradle:7.6.4-jdk17@sha256:c3fda5a772a35a8a68a4043b7d219f62d46e9790efb0bf2c9d1447a0d956253f AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:22.0.1-alpine3.17 as runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
