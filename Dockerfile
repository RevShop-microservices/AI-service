FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the pre-built JAR file directly
COPY target/*.jar app.jar

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

