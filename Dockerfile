FROM node:22.20.0-alpine@sha256:cb3143549582cc5f74f26f0992cdef4a422b22128cb517f94173a5f910fa4ee7 AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:9.2.1-jdk17@sha256:8f5815b9d93d947a41e1d34d91f36dc613da498519910e43c2138fe541e675c6 AS build
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
