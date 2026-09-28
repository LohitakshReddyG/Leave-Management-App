# Stage 1: Build the React frontend
FROM node:18 AS frontend-build
WORKDIR /app
COPY leave-management-app/frontend/package*.json ./frontend/
RUN cd frontend && npm install
COPY leave-management-app/frontend/ ./frontend/
RUN cd frontend && npm run build

# Stage 2: Build the Spring Boot backend
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY leave-management-app/pom.xml .
COPY leave-management-app/src ./src
# Copy the built frontend (which outputs to ../src/main/resources/static based on vite.config.js)
COPY --from=frontend-build /app/src/main/resources/static ./src/main/resources/static
RUN mvn clean package -DskipTests

# Stage 3: Run the application
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
