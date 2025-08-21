# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace/app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy source code
COPY src src

# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Add a user to run the application (security best practice)
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built artifact from the build stage
COPY --from=build /workspace/app/build/libs/*.jar app.jar

# Change ownership of the JAR file
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose the port the application runs on
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]