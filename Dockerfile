FROM node:26.13.1-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:9.6.1-jdk17@sha256:aba72d36b08b131dfb7bd420802a629d137b98840e039d25eb0bfaff7206e4a9 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew build.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle tools tools
RUN gradle clean build --no-daemon

COPY --chown=gradle:gradle src src
COPY --from=scripts /node_modules/jquery/dist/jquery.min.js src/main/resources/public/jquery.js
RUN gradle clean build installDist --no-daemon

FROM amazoncorretto:25.0.5-alpine3.23@sha256:9a687aae4a7b816972bf93cfe2f8a5914004bdcce632739f128354f253a381c6 AS runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
