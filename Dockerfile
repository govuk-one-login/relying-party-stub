FROM node:lts AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:7.6.4-jdk17@sha256:75fae1c4060140b337a22567cace3971dd41056c52809d4b11dcc5b2d2e7a72c AS build
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
