FROM node:26.3.0-alpine@sha256:144769ec3f32e8ee36b3cfde91e82bee25d9367b20f31a151f3f7eea3a2a8541 AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:9.5.1-jdk17@sha256:dda01f5161b21d12403b978e3c020478da9d85c2d4c0c76aeca7df7c83eb6c53 AS build
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
