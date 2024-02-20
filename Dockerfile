FROM gradle:7.6.4-jdk17@sha256:7187ae511aad2f25e86d204dce0ee4a25aac974adc382d631b98976d0eaceb3d AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:17.0.10-alpine3.17 as runtime
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
