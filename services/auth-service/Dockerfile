# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Patche les paquets Alpine du base image (CVE libexpat/p11-kit détectés par Trivy en CI)
RUN apk upgrade --no-cache && addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/target/auth-service.jar app.jar

# Footprint mémoire borné explicitement plutôt que laissé aux heuristiques du JVM
# (MaxRAMPercentage) — le VPS de prod a peu de RAM à partager entre plusieurs
# services (voir docs/deployment/README.md#dimensionnement-mémoire). SerialGC :
# moins d'overhead mémoire que G1 pour un petit tas mono-instance à faible trafic.
# Remplaçable au déploiement via la variable d'environnement JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-Xms192m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
