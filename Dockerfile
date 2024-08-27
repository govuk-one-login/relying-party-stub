FROM gradle:7.6.4-jdk17@sha256:b6763eeac7b7945117d75bc3b9186b86af296c930ecccace3c831ab08109b8f3 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:22.0.2-alpine3.17 as runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
