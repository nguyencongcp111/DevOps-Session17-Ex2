# Dockerfile dung chung cho ca hai service. Chon service bang build arg SERVICE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY user-service user-service
COPY order-service order-service
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /build/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
