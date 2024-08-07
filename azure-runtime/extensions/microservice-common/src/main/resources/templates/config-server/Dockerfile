FROM maven:3.9.7-eclipse-temurin-21 AS maven
MAINTAINER microsoft.com
COPY src config-server-service/src
COPY pom.xml config-server-service/
RUN mvn package -f config-server-service/pom.xml
RUN cp config-server-service/target/*.jar /config-server-service.jar
ENTRYPOINT ["java","-jar","/config-server-service.jar"]
