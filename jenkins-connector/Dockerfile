# Build stage
FROM gradle:8.8-jdk21-alpine AS builder

WORKDIR /build

# Copy Gradle wrapper and build files
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle gradle.properties ./

# Download dependencies (cache layer)
RUN ./gradlew --no-daemon dependencies

# Copy source code and build
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# Runtime stage
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Copy JAR from build stage  
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8081

# Use layered JAR for better performance
ENTRYPOINT ["java", "-jar", "app.jar"]