# ============================================================
#  QarzBot — RUNTIME image (oldindan yig'ilgan jar'dan).
#
#  Korporativ TLS-intercepting proxy ortida konteyner ichida Maven
#  dependency yuklab ololmaydi (PKIX/certificate_unknown). Shuning uchun
#  jar HOST'da (yoki CI'da, toza tarmoqda) yig'iladi, bu image faqat ishlatadi.
#
#  Jar yig'ish:   mvn -o clean package -DskipTests
#  Image qurish:  docker compose build      (yoki docker compose up -d --build)
# ============================================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Root bo'lmagan foydalanuvchi (xavfsizlik)
RUN useradd -r -u 1001 qarzbot
USER qarzbot

# Host'da/CI'da yig'ilgan jar (artifactId-version = qarz-bot-1.0.0)
COPY target/qarz-bot-1.0.0.jar /app/app.jar

EXPOSE 8080

# Token, admin-ids, DB ulanishi — environment (.env / compose) orqali
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
