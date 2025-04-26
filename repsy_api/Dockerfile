# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory in the container
WORKDIR /app

# Argument to specify the JAR file path (relative to the build context)
# The default value assumes the standard Spring Boot JAR naming convention
ARG JAR_FILE=target/repsy_api-0.0.1-SNAPSHOT.jar

# Copy the executable JAR file to the container
COPY ${JAR_FILE} app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the JAR file when the container launches
ENTRYPOINT ["java","-jar","/app/app.jar"] 