# ---- Build stage: package the app with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first: only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the executable jar (tests run separately via `mvn test`).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE, no build tooling ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
