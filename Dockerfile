# ============================================================
#  QarzBot — multi-stage Docker image
#  1-bosqich: Maven bilan jar yig'iladi
#  2-bosqich: faqat JRE + jar (kichik, yengil image)
# ============================================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Korporativ TLS-intercepting proxy (PKIX path building failed) muammosi uchun:
# konteyner JVM proxy'ning privat CA'siga ishonmaydi — Maven'da TLS tekshiruvini chetlab o'tamiz.
ENV MAVEN_OPTS="-Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

# Aliyun mirror (settings.xml repo ildizida) — tez va ishonchli yuklab olish
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests \
    && cp target/qarz-bot-*.jar /app/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Root bo'lmagan foydalanuvchi (xavfsizlik)
RUN useradd -r -u 1001 qarzbot
USER qarzbot

COPY --from=build /app/app.jar /app/app.jar

# Web admin / actuator porti
EXPOSE 8080

# Token, admin-ids, DB ulanishi — environment orqali beriladi (quyidagi .env / compose)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
