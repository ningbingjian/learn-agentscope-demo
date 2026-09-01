FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/55-ProductionAgentArchitecture-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
