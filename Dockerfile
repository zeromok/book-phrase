# ── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Gradle wrapper 먼저 복사 (캐시 최적화)
COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

# 의존성만 먼저 다운로드 (레이어 캐시 활용)
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY src/ src/
RUN ./gradlew clean bootJar -x test --no-daemon

# ── Stage 2: Run ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
