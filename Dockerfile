# Stage 1: Build
# maven:3.9.6-eclipse-temurin-17 has multi-arch support (amd64 + arm64)
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM first — dependency layer is cached unless pom.xml changes
COPY backend/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q

# Copy source and build fat-jar
COPY backend/src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
# eclipse-temurin:17-jre has arm64 + amd64 multi-arch manifest
FROM eclipse-temurin:17-jre AS runtime

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY --from=builder /build/target/pv-cases-*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
