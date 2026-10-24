# Receta para que Render arme y encienda el servidor (no hace falta tocarla).
# 1) Compila el proyecto con Maven
FROM maven:3.9-eclipse-temurin-17 AS compilar
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# 2) Lo ejecuta con Java 17
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=compilar /app/target/panaderia-cloud.jar app.jar
ENV TZ=America/Guatemala
EXPOSE 8080
CMD ["sh", "-c", "java -Xmx320m -jar app.jar"]
