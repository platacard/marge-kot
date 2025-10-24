FROM gradle:8.5-jdk17 AS builder
WORKDIR /project

COPY build.gradle.kts ./
COPY src ./src

RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine AS backend
WORKDIR /root

COPY --from=builder /project/build/libs/*.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/root/app.jar"]