FROM node:26.3.0-alpine@sha256:3ad34ca6292aec4a91d8ddeb9229e29d9c2f689efd0dd242860889ac71842eba AS scripts

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
