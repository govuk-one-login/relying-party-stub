FROM node:26.3.0-alpine@sha256:144769ec3f32e8ee36b3cfde91e82bee25d9367b20f31a151f3f7eea3a2a8541 AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:8.14.3-jdk17@sha256:71624f9e8bdbbccb9fe13717579faafce0d52846eb5048bf6567691c8d933de8 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
COPY --from=scripts /node_modules/jquery/dist/jquery.min.js src/main/resources/public/jquery.js
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:26.0.1-alpine3.23@sha256:5f73844deb7a511f1f3f257525968a5175cd669b9d1abb039db21682c97cff8d AS runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
