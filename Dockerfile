# Use a slim Java runtime as the base image
FROM eclipse-temurin:17-jre

# Set the working directory inside the container
WORKDIR /app

# Copy the UberJAR built by Leiningen
COPY target/uberjar/vv-otx-service-0.0.7-standalone.jar /app/vv-otx-service.jar

# Expose the port the service runs on (8080)
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/app/vv-otx-service.jar"]