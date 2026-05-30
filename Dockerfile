# Stage 1: Build the application inside a Maven container
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy the pom.xml and source files
COPY pom.xml .
COPY src ./src

# Compile and package the application, skipping tests and disabling compiler forking to optimize memory
RUN mvn clean package -DskipTests -Dmaven.compiler.fork=false

# Stage 2: Create the final lightweight runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the packaged JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run as non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
