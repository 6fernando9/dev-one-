FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY server-product/pom.xml server-product/

RUN mvn dependency:go-offline -B || true

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends git util-linux unzip bash curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/server-product/target/onedev-*.zip /app/onedev.zip

RUN unzip /app/onedev.zip -d /tmp/extracted && \
    cp -rf /tmp/extracted/onedev-*/* /app/ && \
    rm -rf /app/onedev.zip /tmp/extracted && \
    chmod +x /app/bin/*.sh /app/boot/wrapper-*

EXPOSE 6610

# CAMBIO AQUÍ: Usar exec para transmitir señales SIGTERM a Java
ENTRYPOINT ["sh", "-c", "exec /app/bin/server.sh console"]