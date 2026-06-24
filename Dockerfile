# ============================================================
#  QarzBot — multi-stage Docker image
#  1-bosqich: Maven bilan jar yig'iladi
#  2-bosqich: faqat JRE + jar (kichik, yengil image)
# ============================================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Aliyun mirror (settings.xml repo ildizida) — dependencylar tez va ishonchli yuklanadi
COPY settings.xml /root/.m2/settings.xml

# Avval pom — dependency layer'i cache'lansin (kod o'zgarsa qayta yuklanmaydi)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# Endi kod
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
