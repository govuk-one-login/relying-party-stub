FROM node:26.7.0-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS scripts

COPY scripts .
RUN npm install --ignore-scripts

FROM gradle:9.9.1-jdk17@sha256:1b8675f710da5ad837e711758886dc63524877a3efa9ee96ffd2e4f6fef07211 AS build
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
