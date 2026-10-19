FROM eclipse-temurin:21-jdk

WORKDIR /app

# Vygenerování SSL certifikátu do bezpečné složky mimo /app
RUN mkdir -p /certs && \
    keytool -genkeypair -alias ems-backend \
    -keyalg RSA -keysize 2048 \
    -storetype PKCS12 \
    -keystore /certs/keystore.p12 \
    -validity 3650 \
    -storepass secret123 \
    -dname "CN=localhost, OU=EMS, O=UPCE, L=Pardubice, C=CZ"

EXPOSE 8443

# Spuštění přes Gradle bootRun (reaguje na změny v souborech)
CMD ["./gradlew", "bootRun", "--no-daemon"]