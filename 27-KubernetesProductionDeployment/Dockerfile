FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -pl 27-KubernetesProductionDeployment -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/27-KubernetesProductionDeployment/target/27-KubernetesProductionDeployment-1.0-SNAPSHOT.jar app.jar
EXPOSE 18081 18082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
