# Build from the repository root:
#   docker build -f services/bridge/Dockerfile -t adc-bridge .

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :services:bridge:buildFatJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/services/bridge/build/libs/bridge-all.jar /app/bridge.jar
USER 10001
EXPOSE 8080
# Health is checked by the load balancer on GET /health.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/bridge.jar"]
