FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN ./gradlew --version --no-daemon
COPY src src
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/RollcallBot.jar RollcallBot.jar
VOLUME ["/app/storage", "/app/logs"]
ENTRYPOINT ["java", "-jar", "RollcallBot.jar"]
