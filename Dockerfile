# syntax=docker/dockerfile:1

# One image for all five FleetFlow services: the reactor is built once, and
# docker-compose.yml picks which jar each container runs.

FROM maven:3.9.16-eclipse-temurin-21-noble AS build
WORKDIR /src
COPY . .
# The cache mount keeps downloaded dependencies between builds. Tests are not
# compiled here; run them with `mvn verify` on the host.
RUN --mount=type=cache,target=/root/.m2 mvn -B package -Dmaven.test.skip=true

FROM eclipse-temurin:21.0.12_8-jre-noble
# Each service writes its H2 file under ./data, i.e. /app/data in the container.
WORKDIR /app
COPY --from=build /src/order-service/target/order-service-0.1.0-SNAPSHOT.jar order-service.jar
COPY --from=build /src/fleet-service/target/fleet-service-0.1.0-SNAPSHOT.jar fleet-service.jar
COPY --from=build /src/dispatch-service/target/dispatch-service-0.1.0-SNAPSHOT.jar dispatch-service.jar
COPY --from=build /src/tracking-service/target/tracking-service-0.1.0-SNAPSHOT.jar tracking-service.jar
COPY --from=build /src/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar notification-service.jar
