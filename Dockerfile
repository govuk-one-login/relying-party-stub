FROM node:lts AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:8.14.0-jdk17@sha256:292f8efec195c0d7c0e543d5b06600c0a0501a860d6895bad902b36c6cd6c9ac AS build
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
