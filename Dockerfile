FROM gradle:7.6.3-jdk17@sha256:8eb7a6c7606b0189fe08e4137a0d2585f0d68f85298a6e2a6f31380a7d5f4242 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:17.0.8-alpine3.17 as runtime
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
