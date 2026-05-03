# TruthScope Backend — 코딩 규칙

> 상세 가이드: `CONVENTIONS.md` 참조
> 커밋 메시지: Gitmoji 사용 (팀 규칙)

---

## 기술 스택
- Spring Boot 3.x (Java 17+)
- JPA / Hibernate + Supabase PostgreSQL
- Lombok

## 엔드포인트

- 모든 API: `/api/v1/{자원복수형}` — `/api`만 쓰지 않는다
- URI에 동사 금지, 복수형 명사, 하이픈(-) 구분, 소문자만
- HTTP Method: GET(조회), POST(생성), PATCH(부분수정), DELETE(삭제)
- PUT은 전체 교체 시에만 — 대부분 PATCH 사용

## 레이어 규칙

- Controller → Service → Repository → Entity (역방향 금지)
- Controller: URL 매핑 + 요청/응답만. 비즈니스 로직 금지
- Service: `@Transactional` (쓰기), `@Transactional(readOnly = true)` (읽기)
- Entity를 Controller/응답에 직접 노출 금지 → 반드시 DTO로 변환

## 클래스/파일 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| Controller | `{도메인}Controller` | `NewsController` |
| Service | `{기능}Service` | `ContentExtractService` |
| Repository | `{Entity}Repository` | `ArticleRepository` |
| Entity | 테이블명 단수형 PascalCase | `Member`, `AnalysisSession` |
| DTO 요청 | `{도메인}Request` | `AnalysisRequest` |
| DTO 응답 | `{도메인}Response` | `ArticleResponse` |
| Converter | `{도메인}Converter` | `ArticleConverter` |

## Entity

- `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder` + `@AllArgsConstructor`
- `@Data`, `@ToString` 절대 금지
- `@Enumerated(EnumType.STRING)` 필수 — ORDINAL 금지
- `@Setter` 사용하지 않음 — 변경은 비즈니스 메서드로
- 모든 관계: `fetch = FetchType.LAZY` 필수
- `extends BaseTimeEntity` (ApiUsageLog만 예외)
- PK: `@GeneratedValue(strategy = GenerationType.UUID)` (Member만 예외 — Supabase Auth UUID)

## DTO

- 요청: `record` 타입 (Java 17+) + `@Valid` 검증
- 응답: 클래스 + `@Getter` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`
- 패키지: `dto/request/`, `dto/response/`

## Converter

- `converter/` 패키지에 `{도메인}Converter` 클래스
- `@NoArgsConstructor(access = PRIVATE)`, static 메서드만
- Service에서 호출 (Controller 아님)

## 응답 형식

- 단순 200: 객체 직접 반환 (ResponseEntity 불필요)
- 201/204 등 상태코드 제어 필요 시만 ResponseEntity 사용
- 에러: GlobalExceptionHandler가 자동 처리 → `{ status, statusCode, message }`
- 페이지네이션: VIA_DTO 모드 사용 — 커스텀 PageResponse 만들지 않는다

## DI

- 생성자 주입만 허용 (`@RequiredArgsConstructor` + `private final`)
- `@Autowired` 필드 주입 금지

## 환경 설정

- `.env` 파일 사용 금지 — `application.yml` + `application-local.yml`
- `application-local.yml`은 Git 금지

## 포맷

- Google Java Format (Spotless): `./gradlew spotlessApply`
- 빌드 전 반드시 포맷 적용

## spike sourceSet — throwaway 연구 코드 격리 (2026-05-04 도입)

평소 build와 분리해서 운영하는 **연구·검증용 throwaway 코드** 전용 sourceSet. 외부 API/데이터소스 정합성 측정 같은 비-프로덕션 코드를 main test classpath에 섞지 않기 위해 도입.

### 위치

| 종류 | 경로 |
|---|---|
| Java 소스 | `src/spike/java/` (패키지 자유) |
| 리소스 (CSV, fixtures 등) | `src/spike/resources/` |

### 정책

| 항목 | 정책 |
|---|---|
| Spring 의존 | **금지** — Plain Java + JUnit 5만. 필요하면 별 module로 분리 |
| Spotless (포맷) | 적용 — 협업 가능한 코드 스타일 유지 |
| Checkstyle | **비활성화** (`checkstyleSpike.enabled = false`) |
| SpotBugs | **비활성화** (`spotbugsSpike.enabled = false`) — 연구 코드 false positive 무시 |
| 평소 `./gradlew test` | **자동 제외** — 별 sourceSet이라 test classpath 미공유 |
| 명시 실행 | `./gradlew spikeTest` |
| Throwaway 권장 어노테이션 | `@Tag("spike")` + `@Disabled("spike — verified YYYY-MM-DD. Enable manually to re-run.")` |

### build.gradle 설정 (이미 적용됨)

```groovy
sourceSets {
    spike {
        java { srcDirs = ['src/spike/java'] }
        resources { srcDirs = ['src/spike/resources'] }
        compileClasspath += sourceSets.test.runtimeClasspath
        runtimeClasspath += sourceSets.test.runtimeClasspath
    }
}

configurations {
    spikeImplementation.extendsFrom testImplementation
    spikeRuntimeOnly.extendsFrom testRuntimeOnly
}

tasks.register('spikeTest', Test) { ... }

tasks.matching { it.name in ['checkstyleSpike', 'spotbugsSpike'] }.configureEach {
    enabled = false
}
```

### 사용 시점 (스파이크 추가)

1. 새 spike 디렉토리: `src/spike/java/{도메인}/...`
2. 패키지 자유 (`com.truthscope.web.spike.{도메인}` 권장 but 강제 아님)
3. 리소스: `src/spike/resources/{도메인}/...`
4. 클래스 상단에 `@Tag("spike")` + `@Disabled` (검증 완료 후 영구 보존이라면)
5. 처음 실행: `./gradlew spikeTest --tests "...{TestName}"` (또는 `@Disabled` 제거 후 `./gradlew spikeTest`)

### 사용 시점 (검증 졸업 시)

spike 코드가 본 프로덕션 패턴으로 졸업하면 → `src/spike/`에서 `src/main/` + `src/test/`로 정식 마이그레이션. spike sourceSet은 **연구 단계 임시 보관소**.

### 현재 spike 입주 작업

- `src/spike/java/com/truthscope/web/spike/datasourceaccuracy/` — 데이터 소스 6개 정합성 측정 (2026-04-24 PM-spike rev.5/6) — 4 클래스
- `src/spike/resources/spike/datasource-accuracy/` — gold-set + 결과 CSV 4개

---

## Gemini 모델 — 절대 변경 금지

- 1순위: `gemini-3.1-flash-lite-preview`
- 2순위 폴백: `gemini-2.5-flash-lite`
- `gemini-2.0-flash-lite` 언급/사용 금지
