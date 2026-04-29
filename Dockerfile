FROM node:22.20.0-alpine@sha256:cb3143549582cc5f74f26f0992cdef4a422b22128cb517f94173a5f910fa4ee7 AS scripts

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

FROM amazoncorretto:21.0.10-alpine3.23@sha256:29fb96b59042d9637e3d51ff3b423be3a88f17f530cbfb767a91d4199457f395 AS runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
