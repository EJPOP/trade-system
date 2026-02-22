# AGENTS.md

## 프로젝트 루트 정책 (trade-system)

### 루트에 유지할 항목 (필수)
- `src/` : Spring Boot Java 소스
- `gradle/`, `gradlew`, `gradlew.bat` : Gradle 래퍼
- `build.gradle`, `settings.gradle` : 빌드 설정
- `.gitignore`, `.gitattributes` : 저장소 설정
- `.reffiles/` : 참고 자료 보관소
- `AGENTS.md`, `krx-command.md` : 운영 기준 문서

### 빌드/실행
- 컴파일: `./gradlew compileJava -q`
- 애플리케이션 실행: `./gradlew bootRun`
- 실행 명령어 참고: `krx-command.md`

### 실행 필수 설정
- DB 및 KRX 설정 파일: `src/main/resources/application.yml`
- 필수 키:
  - `spring.datasource.*`
  - `krx.base-url`, `krx.auth-key`, `krx.timeout-seconds`, `krx.response-charset`

### 루트 정리 규칙
- 빌드/실행에 필요 없는 파일은 루트에 두지 않습니다.
- DB 스크립트/모델은 `.reffiles/db-create-script/`로 이동합니다.
- OpenAPI 원문/사양 문서는 `.reffiles/krx-openapi-guide/`로 이동합니다.
- IDE/빌드 산출물(`.idea/`, `build/`)은 커밋 대상에서 제외합니다.

### 루트에서 이동된 자료
- DB 스크립트/모델: `.reffiles/db-create-script/`
- KRX OpenAPI 가이드: `.reffiles/krx-openapi-guide/`
