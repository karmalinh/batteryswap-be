# ---------- Stage 1: Build with Maven Wrapper ----------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy toàn bộ dự án
COPY . .

# Cho phép mvnw chạy
RUN chmod +x mvnw

# Build project (bỏ chạy test)
RUN ./mvnw -DskipTests package

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar đã build từ stage 1
COPY --from=builder /app/target/batteryswap-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
