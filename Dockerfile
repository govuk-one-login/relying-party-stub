FROM node:26.7.0-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS scripts

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

FROM amazoncorretto:26.0.2-alpine3.23@sha256:0f2081ac91b91d03ac212be140051b0e1693b2ace2d60f305b2597e2ec776346 AS runtime
WORKDIR /home
COPY --from=build /home/gradle/src/build/install/src .

ENTRYPOINT ["bin/src"]
