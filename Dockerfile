# ---- build: compile, then split the jar into layers ---------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# Spring Boot 3.3+ replaced `-Djarmode=layertools` with `-Djarmode=tools extract --layers`.
# The 144 MB `dependencies` layer rarely changes; only the small `application` layer does.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# ---- runtime -------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Dedicated unprivileged user with a fixed uid/gid (works with runAsNonRoot-style policies).
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

# Least to most frequently changing, so a code-only change rebuilds only the last layer.
# Files stay root-owned and read-only for the app user; the app only writes to /tmp.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app:app

# Override with -e JAVA_OPTS=... if needed. The heap is sized from the container memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthy only when /actuator/health answers 2xx (it answers 503 when e.g. the database is down).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# `exec` makes the JVM PID 1 so it receives SIGTERM and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
