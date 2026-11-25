# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Build scripts first: the dependency layer is reused until they change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# The IP-to-country database is fetched with every image build, so a monthly rebuild ships fresh data.
RUN ./gradlew --no-daemon downloadGeoDatabase bootJar \
 && java -Djarmode=tools -jar build/libs/app.jar extract --destination /application \
 && mkdir /application/geo && cp build/geo/dbip-country-lite.mmdb /application/geo/

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /application
# Extracted layout (application jar + lib/) starts faster than the fat jar.
COPY --from=build /application ./
USER app
EXPOSE 8080
# Compact object headers (JEP 519, a product feature since Java 25) shrink every object header to 8 bytes.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseCompactObjectHeaders"
ENV GEO_DATABASE=file:/application/geo/dbip-country-lite.mmdb
ENTRYPOINT ["java", "-jar", "app.jar"]
