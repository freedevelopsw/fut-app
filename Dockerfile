# Imagem da FutApp para o Google Cloud Run (ou outro alojamento de contentores).
# 1) compila a aplicação com o Maven; 2) corre o jar só com o Java runtime.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/futapp-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
# Memória à medida do contentor e arranque mais rápido (importante quando a app acorda)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
