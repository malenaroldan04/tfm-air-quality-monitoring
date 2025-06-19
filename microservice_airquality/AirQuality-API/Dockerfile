#Compilación
FROM maven:3.8.5-openjdk-17-slim AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package

#Imagen final
FROM openjdk:17-slim

WORKDIR /app

COPY --from=build /app/target/AirQuality-API-1.0-SNAPSHOT-fat.jar /app/air-quality-api.jar

EXPOSE 8888

CMD ["java", "-jar", "air-quality-api.jar"]