FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="mine-com-server"
LABEL org.opencontainers.image.description="Minecraft server management backend"

RUN addgroup -S mcapp && adduser -S mcapp -G mcapp

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN mkdir -p /app/uploads/avatars && chown -R mcapp:mcapp /app

USER mcapp

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
