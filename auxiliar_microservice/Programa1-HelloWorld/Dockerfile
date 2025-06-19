#Usa una imagen base de Maven para construir la aplicación
FROM maven:3.8.5-openjdk-17-slim AS build

#Establece el directorio de trabajo en el contenedor
WORKDIR /app

#Copia los archivos de configuración de Maven
COPY pom.xml .

#Descarga las dependencias de Maven
RUN mvn dependency:go-offline

#Copia el código fuente de la aplicación al contenedor
COPY src ./src

#Construye la aplicación utilizando Maven
RUN mvn clean package

#Usa una imagen base de OpenJDK para ejecutar la aplicación
FROM openjdk:17-slim

#Establece el directorio de trabajo en el contenedor
WORKDIR /app

#Copia el archivo JAR de la aplicación desde la fase de construcción al contenedor
COPY --from=build /app/target/starter-1.0.0-SNAPSHOT-fat.jar /app/aplicacion-prueba-vertx.jar

#Expone el puerto en el que corre la aplicacion
EXPOSE 8888

#Comando para ejecutar la aplicación
CMD ["java", "-jar", "aplicacion-prueba-vertx.jar"]
