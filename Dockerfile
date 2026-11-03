# syntax=docker/dockerfile:1

# Build stage - compiles the jar with the Maven wrapper, no local Maven install needed.
# Tests are skipped here: CI (.github/workflows/ci.yml) already runs the full `mvn verify`
# (unit + integration + PMD + JaCoCo) on every push/PR; re-running them (and needing
# Docker-in-Docker for Testcontainers) inside the image build would be redundant and slow.
FROM eclipse-temurin:25-jdk-alpine@sha256:425f0b733cb23843bd78dbe5552513976d0cf12b9722752c48a5161cf5cd2f32 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw is checked out with CRLF line endings on Windows (git autocrlf) - strip the \r before
# executing it, or Alpine's /bin/sh fails on the shebang line with "not found".
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
# `package` leaves both the repackaged executable jar and Maven's original plain jar
# (*.jar.original) in target/ - exclude the latter so the glob below is unambiguous.
RUN ./mvnw -q -DskipTests package && \
    find target -maxdepth 1 -name '*.jar' ! -name '*.jar.original' -exec mv {} target/app.jar \;

# Runtime stage - JRE only, non-root user, no build toolchain in the shipped image.
FROM eclipse-temurin:25-jre-alpine@sha256:56f273059de6b13a6b49fe0ded691ed2d0399bbee76156a8f2d385f7524a3d43
RUN apk upgrade --no-cache
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
