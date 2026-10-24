# Build the application using Maven and Java 17
FROM maven:3.10.0-eclipse-temurin-17-noble AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp -DskipTests clean package

# Run the packaged application
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system trustvault \
    && useradd --system --gid trustvault --no-create-home \
       --shell /usr/sbin/nologin trustvault

COPY --from=build --chown=trustvault:trustvault /build/target/*.jar ./app.jar
COPY --chown=trustvault:trustvault certs/aiven-truststore.p12 ./certs/aiven-truststore.p12

ENV PORT=10000 \
    SPRING_PROFILES_ACTIVE=cloud

USER trustvault
EXPOSE 10000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "/app/app.jar"]