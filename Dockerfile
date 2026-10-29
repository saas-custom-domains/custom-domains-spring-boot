FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/billport-*.jar /app.jar

FROM eclipse-temurin:25-jre
# curl for the compose healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system app && mkdir /data && chown app /data
USER app
ENV PORT=3000 DATA_DIR=/data
COPY --from=build /app.jar /app.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app.jar"]
