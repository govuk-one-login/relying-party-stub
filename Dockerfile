FROM node:lts AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:8.14.0-jdk17@sha256:e92df1d16c3552a4a5127a5e43294ee255183131c682063408ddf920cfc993d3 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
COPY --from=scripts /node_modules/jquery/dist/jquery.min.js src/main/resources/public/jquery.js
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:22.0.2-alpine3.17 AS runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
