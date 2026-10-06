FROM eclipse-temurin:8-jdk-jammy AS java8
FROM eclipse-temurin:21-jdk-jammy
COPY --from=java8 /opt/java/openjdk /opt/java/jdk8
ENV JAVA_HOME_8_X64=/opt/java/jdk8
ENV JAVA_HOME_21_X64=/opt/java/openjdk
RUN apt-get update && apt-get install -y --no-install-recommends git python3 curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*
