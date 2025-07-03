FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/uberjar/vv-otx-service-0.0.8-standalone.jar /app/vv-otx-service.jar
COPY public/index.html /app/public/index.html
# Create directory for static files
RUN mkdir -p /app/public/js
COPY public/js/main.js /app/public/js/main.js
EXPOSE 8080
CMD ["java", "-jar", "/app/vv-otx-service.jar"]