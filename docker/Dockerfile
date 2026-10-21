# 모든 Spring Boot 모듈이 같이 쓰는 Dockerfile (docker-compose.yml 에서 build.context 로 모듈 폴더를 지정)
# 1단계: 모듈의 gradlew 로 bootJar 빌드 / 2단계: JRE 이미지에서 실행

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY . .

# Windows 에서 체크아웃하면 gradlew 가 CRLF 일 수 있어서 LF 로 바꾸고 실행 권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 테스트는 이미지 빌드에서 제외 (로컬/CI 에서 따로 실행)
# 모듈들이 동시에 빌드되면서 같은 Gradle 캐시를 쓰면 wrapper 다운로드 잠금에서 타임아웃이 나므로 sharing=locked 로 한 번에 하나씩 사용
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar -x test --no-daemon

# plain jar 를 제외한 실행 가능한 jar 하나만 app.jar 로 복사
RUN find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
