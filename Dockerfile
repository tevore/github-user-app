# Use a lightweight Java 17 runtime
FROM openjdk:17.0.2-jdk-slim

# Set working directory
WORKDIR /app

# Copy the Spring Boot jar
COPY build/libs/*.jar app.jar

# Expose application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
