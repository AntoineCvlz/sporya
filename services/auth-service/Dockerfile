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

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
