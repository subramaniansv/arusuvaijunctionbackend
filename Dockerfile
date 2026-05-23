# ─── Stage 1: build the uber-jar ────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

# Compile + shade
COPY src ./src
RUN mvn -B -q -e -DskipTests package

# ─── Stage 2: slim runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for safety
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/ecommerce.jar /app/app.jar
USER app

# Render injects $PORT at runtime; the app reads it via System.getenv("PORT").
ENV PORT=8080 \
    CONTEXT_PATH=/arusuvai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 8080

# Render's health check probes / by default — point it at /arusuvai/health
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT}${CONTEXT_PATH}/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
