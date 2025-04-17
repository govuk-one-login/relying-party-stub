FROM node:lts AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:8.13.0-jdk17@sha256:10a7534228da8a75c6e77904a5d3c857f92070e842e4bef3f9fe89792d0d3f50 AS build
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
