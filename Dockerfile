
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests

FROM tomcat:11.0-jdk17-temurin

COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Configure Tomcat to use Render's PORT, defaulting to 8080 locally.
RUN sed -i 's/port="8080"/port="${PORT:8080}"/' /usr/local/tomcat/conf/server.xml

EXPOSE 8080

