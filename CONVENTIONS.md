# TruthScope Web Backend — 코딩 컨벤션 가이드

> 이 문서는 팀원이 코드를 작성할 때 참고하는 규칙입니다.

---

## 1. 패키지 구조 (레이어드 아키텍처)

```
com.truthscope.web/
├── config/          ← 설정 클래스 (Security, CORS 등)
├── controller/      ← REST API 엔드포인트
├── service/         ← 비즈니스 로직
├── repository/      ← DB 접근 (JPA Repository)
├── entity/          ← JPA Entity 클래스
│   └── enums/       ← Enum 타입 정의
├── dto/             ← 요청/응답 DTO
│   ├── request/     ← 요청 DTO
│   └── response/    ← 응답 DTO
├── converter/       ← Entity ↔ DTO 변환 전담
└── exception/       ← 커스텀 예외 클래스
```

### 핵심 규칙: 의존 방향

**위에서 아래로만 의존 가능.** 역방향 의존 금지.

```
Client ←DTO→ Controller ←DTO→ Service ←Entity→ Repository ←Entity→ DB
                                  ↕
                              Converter (Entity ↔ DTO 변환)
```

```java
// ✅ 허용 — Controller가 Service를 호출
@RequiredArgsConstructor
public class NewsController {
    private final ContentExtractService contentExtractService;
}

// ✅ 허용 — Service가 Repository를 호출
@RequiredArgsConstructor
public class ContentExtractService {
    private final ArticleRepository articleRepository;
}

// ❌ 금지 — Repository가 Service를 호출
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    // Service를 주입받으면 안 됨!
}
```

---

## 2. 클래스/파일 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| **패키지** | 소문자 단일 단어 | `controller`, `service`, `entity` |
| **클래스** | PascalCase | `ContentExtractService`, `Article` |
| **Controller** | `{도메인}Controller` | `NewsController`, `FactCheckController` |
| **Service** | `{기능}Service` | `ContentExtractService`, `ClaimAnalysisService` |
| **Repository** | `{Entity}Repository` | `ArticleRepository`, `MemberRepository` |
| **Entity** | 테이블명의 단수형 PascalCase | `Member`, `AnalysisSession` |
| **DTO (요청)** | `{도메인}Request` | `AnalysisRequest`, `MemberRequest` |
| **DTO (응답)** | `{도메인}Response` | `AnalysisResponse`, `ArticleResponse` |
| **Converter** | `{도메인}Converter` | `ArticleConverter`, `ClaimConverter` |
| **Exception** | `{상황}Exception` | `NotFoundException`, `BadRequestException` |
| **Enum** | PascalCase | `SessionStatus`, `MemberRole` |
| **변수/메서드** | camelCase | `articleRepository`, `extractContent()` |
| **상수** | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |

### 클래스 내부 순서

1. 어노테이션 → 클래스 선언
2. 상수(static final) → 멤버 변수
3. 생성자 → 메서드
4. public → protected → private 순서
5. CRUD 순서: Create → Read → Update → Delete

---

## 3. REST 엔드포인트 규칙

### URL 구조

모든 API는 `/api/v1/`로 시작합니다.

```
/api/v1/{자원}              ← 목록 조회, 생성
/api/v1/{자원}/{id}         ← 단건 조회, 수정, 삭제
/api/v1/{자원}/{id}/{하위자원}  ← 하위 자원 접근
```

### URL 네이밍 규칙

| 규칙 | 잘못된 예 | 올바른 예 |
|------|----------|----------|
| 자원은 **복수형 명사** | `/article/{id}` | `/articles/{id}` |
| URI에 **동사 금지** — HTTP Method가 행위 표현 | `/delete-article/{id}` | `DELETE /articles/{id}` |
| 단어 구분은 **하이픈(-)** | `/fact_checks` | `/fact-checks` |
| 소문자만 사용 | `/Articles` | `/articles` |
| 마지막에 `/` 붙이지 않기 | `/articles/` | `/articles` |

### HTTP Method 사용 규칙

| Method | 용도 | 예시 |
|--------|------|------|
| `GET` | 조회 (단건/목록) | `GET /api/v1/articles/{id}` |
| `POST` | 생성 | `POST /api/v1/analysis` |
| `PATCH` | 부분 수정 | `PATCH /api/v1/articles/{id}` |
| `PUT` | 전체 교체 (거의 안 씀) | `PUT /api/v1/articles/{id}` |
| `DELETE` | 삭제 | `DELETE /api/v1/articles/{id}` |

> **PATCH vs PUT**: 부분 수정은 `PATCH`, 전체 교체는 `PUT`. 대부분의 경우 `PATCH`를 사용합니다.

### TruthScope 엔드포인트 예시

| 기능 | Method | URL |
|------|--------|-----|
| 기사 분석 요청 | POST | `/api/v1/analysis` |
| 분석 결과 조회 | GET | `/api/v1/analysis/{sessionId}` |
| 기사 상세 조회 | GET | `/api/v1/articles/{id}` |
| 기사의 Claim 목록 | GET | `/api/v1/articles/{id}/claims` |
| 팩트체크 상세 | GET | `/api/v1/claims/{id}/verification` |
| 사용자 반응 등록 | POST | `/api/v1/articles/{id}/reactions` |
| 헬스체크 | GET | `/api/v1/health` |

---

## 4. 응답 형식

### 성공 응답

```java
// 단순 200 OK — 객체 직접 반환 (간단한 경우)
@GetMapping("/{id}")
public ArticleResponse getArticle(@PathVariable UUID id) {
    return articleService.getArticle(id);
}

// 201 Created — ResponseEntity 사용 (상태코드 제어 필요 시)
@PostMapping
public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
    AnalysisResponse response = analysisService.analyze(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

// 204 No Content — 삭제 시
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable UUID id) {
    articleService.delete(id);
}
```

### 에러 응답 (자동 처리)

GlobalExceptionHandler가 모든 예외를 통일된 형식으로 변환합니다:

```json
{
  "status": "fail",
  "statusCode": 404,
  "message": "기사를 찾을 수 없습니다"
}
```

| status | statusCode | 의미 |
|--------|-----------|------|
| `"fail"` | 400 | 잘못된 요청 (클라이언트 오류) |
| `"fail"` | 401 | 미인증 (토큰 무효/미로그인) |
| `"fail"` | 403 | 권한 없음 (로그인됐지만 접근 불가) |
| `"fail"` | 404 | 리소스 없음 |
| `"fail"` | 409 | 충돌 (중복 데이터) |
| `"error"` | 500 | 서버 내부 오류 |

```json
// 500 에러 예시
{ "status": "error", "statusCode": 500, "message": "서버 내부 오류" }
```

> **401 vs 403**: 401은 "누구인지 모른다"(미로그인), 403은 "누구인지 알지만 권한 없다".

---

## 5. Entity 작성 규칙

### 클래스 선언 패턴

```java
@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Article extends BaseTimeEntity {
    // ...
}
```

### 필수 어노테이션 조합

| 어노테이션 | 용도 | 주의 |
|-----------|------|------|
| `@Entity` + `@Table(name = "...")` | JPA Entity 선언 | 테이블명은 snake_case 복수형 |
| `@Getter` | 필드 접근 | `@Data` 절대 금지 (순환 참조 위험) |
| `@NoArgsConstructor(access = PROTECTED)` | JPA 필수 기본 생성자 | `public` 아닌 `PROTECTED` |
| `@Builder` + `@AllArgsConstructor` | 빌더 패턴 | 둘 다 함께 있어야 컴파일됨 |
| `extends BaseTimeEntity` | 공통 시간 필드 상속 | ApiUsageLog는 예외 — 상속하지 않음 |

### PK 규칙

```java
// 일반 Entity — UUID 자동 생성
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

// Member만 예외 — Supabase Auth가 UUID를 할당하므로 @GeneratedValue 없음
@Id
@Column(columnDefinition = "uuid")
private UUID id;
```

### 연관관계 규칙

```java
// FK 소유 측 (자식) — 반드시 LAZY
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "article_id", nullable = false)
private Article article;

// 비소유 측 (부모) — mappedBy 사용
@OneToMany(mappedBy = "article", cascade = CascadeType.ALL)
private List<Claim> claims = new ArrayList<>();
```

| 규칙 | 이유 |
|------|------|
| `fetch = FetchType.LAZY` 필수 | 기본값 EAGER → N+1 쿼리 성능 문제 |
| `@Enumerated(EnumType.STRING)` 필수 | ORDINAL은 순서 변경 시 데이터 깨짐 |
| `@Data`, `@ToString` 금지 | 양방향 관계에서 StackOverflowError |
| `@Setter` 사용하지 않음 | 영속성 컨텍스트 외부에서 상태 변경 방지 — 변경은 비즈니스 메서드로 |

---

## 6. DTO 작성 규칙

### 요청 DTO — record 사용 (Java 17+)

```java
// dto/request/AnalysisRequest.java
public record AnalysisRequest(
    @NotBlank(message = "URL은 필수입니다")
    String url
) {}
```

> `record`는 불변 객체. 생성자, getter, equals, hashCode, toString이 자동 생성됩니다.
> **Entity에는 record 사용 금지** — Hibernate가 기본 생성자를 요구하기 때문.

### 응답 DTO — 클래스 + Builder

```java
// dto/response/ArticleResponse.java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {
    private UUID id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}
```

### 핵심 원칙

```java
// ✅ Controller에서 DTO로 주고받기
@PostMapping("/analysis")
public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) { ... }

// ❌ Entity를 직접 반환하지 않기
@GetMapping("/articles/{id}")
public ResponseEntity<Article> getArticle(...) { ... }  // Entity 직접 노출 금지!
```

---

## 7. Converter 패턴 (Entity ↔ DTO 변환)

Entity와 DTO 간 변환은 **Converter 클래스**에서 전담합니다.

```java
// converter/ArticleConverter.java
@NoArgsConstructor(access = AccessLevel.PRIVATE)  // 인스턴스 생성 방지
public class ArticleConverter {

    public static ArticleResponse toResponse(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .createdAt(article.getCreatedAt())
                .build();
    }

    public static Article toEntity(AnalysisRequest request) {
        // DDD aggregate Entity는 정적 팩토리(Article.extract)만 사용 — Builder는 PRIVATE 접근으로 차단됨
        return Article.extract(request.url(), null, null, null, null);
    }
}
```

### 사용법

```java
// Service에서 Converter 호출
@Service
@RequiredArgsConstructor
public class ContentExtractService {

    private final ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public ArticleResponse getArticle(UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("기사를 찾을 수 없습니다"));
        return ArticleConverter.toResponse(article);  // Converter로 변환
    }
}
```

| 규칙 | 이유 |
|------|------|
| `@NoArgsConstructor(access = PRIVATE)` | 유틸리티 클래스 — 인스턴스 생성 방지 |
| static 메서드만 사용 | 상태 없는 변환 로직 |
| Service에서 호출 | Controller는 변환 로직을 모름 |

---

## 8. Validation (입력 검증)

### Controller에서 @Valid 적용

```java
@PostMapping("/analysis")
public ResponseEntity<AnalysisResponse> analyze(
        @Valid @RequestBody AnalysisRequest request) {  // @Valid 필수
    // ...
}
```

### 주요 검증 어노테이션

| 어노테이션 | 용도 | 대상 |
|-----------|------|------|
| `@NotBlank` | null + 공백 문자열 차단 | String |
| `@NotNull` | null만 차단 | 모든 타입 |
| `@Size(min, max)` | 문자열/컬렉션 크기 | String, Collection |
| `@Min(value)` / `@Max(value)` | 숫자 범위 | int, long |
| `@URL` | URL 형식 검증 | String |

> 검증 실패 시 GlobalExceptionHandler가 자동으로 400 에러 응답을 반환합니다.

---

## 9. Controller 작성 규칙

```java
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /** 기사 분석 요청 */
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(
            @Valid @RequestBody AnalysisRequest request) {
        AnalysisResponse response = analysisService.analyze(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 분석 결과 조회 */
    @GetMapping("/{sessionId}")
    public AnalysisResponse getAnalysis(@PathVariable UUID sessionId) {
        return analysisService.getAnalysis(sessionId);
    }
}
```

| 규칙 | 설명 |
|------|------|
| `@RequestMapping("/api/v1/{자원}")` | 모든 API 경로는 `/api/v1/`로 시작 |
| `@RequiredArgsConstructor` | 생성자 주입 (필드에 `@Autowired` 금지) |
| 단순 200은 객체 직접 반환 | ResponseEntity는 상태코드 제어 필요 시만 |
| `@Valid` | DTO 유효성 검사 활성화 |
| Controller에 비즈니스 로직 금지 | URL 매핑 + 요청/응답 변환만 담당 |

---

## 10. Service 작성 규칙

```java
@Service
@RequiredArgsConstructor
public class ContentExtractService {

    private final ArticleRepository articleRepository;

    @Transactional
    public AnalysisResponse analyze(AnalysisRequest request) {
        // 비즈니스 로직
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticle(UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("기사를 찾을 수 없습니다"));
        return ArticleConverter.toResponse(article);
    }
}
```

| 규칙 | 설명 |
|------|------|
| `@Transactional` | 쓰기 작업 (CREATE, UPDATE, DELETE) |
| `@Transactional(readOnly = true)` | 읽기 전용 작업 (성능 최적화) |
| `@RequiredArgsConstructor` | 생성자 주입 |
| Entity → DTO 변환은 Converter로 | Service 안에서 직접 변환하지 않기 |

---

## 11. 예외 처리

### 커스텀 예외 계층

```
AppException (부모 — statusCode + message)
├── BadRequestException  (400)
└── NotFoundException    (404)
```

### 사용법

```java
// 예외 throw
throw new NotFoundException("기사를 찾을 수 없습니다");
throw new BadRequestException("유효하지 않은 URL입니다");

// GlobalExceptionHandler가 자동 처리 → ApiErrorResponse로 변환
// { "status": "fail", "statusCode": 404, "message": "기사를 찾을 수 없습니다" }
```

### 새 예외 추가 방법

```java
public class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}
```

> GlobalExceptionHandler가 `AppException`을 잡으므로, 하위 클래스를 추가하면 자동으로 처리됩니다.

---

## 12. 환경변수 / 설정

**`.env` 파일 사용 금지.** Spring Boot는 `application.yml`을 사용합니다.

```
src/main/resources/
├── application.yml          ← 공통 설정 (Git에 올라감)
└── application-local.yml    ← 로컬 DB 비밀번호 등 (Git 금지!)
```

```bash
# 로컬 실행 시 프로필 지정
./gradlew bootRun --args='--spring.profiles.active=local'
```

| 파일 | Git | 용도 |
|------|-----|------|
| `application.yml` | O | 환경변수 플레이스홀더 (`${SUPABASE_HOST:localhost}`) |
| `application-local.yml` | X | 실제 DB 비밀번호 — **절대 커밋 금지** |

---

## 13. 의존성 주입

```java
// ✅ 생성자 주입 (유일하게 허용) — @RequiredArgsConstructor 사용
@Service
@RequiredArgsConstructor
public class ClaimAnalysisService {
    private final ArticleRepository articleRepository;  // final 필수
    private final GeminiClient geminiClient;
}

// ❌ 필드 주입 금지
@Service
public class ClaimAnalysisService {
    @Autowired  // 금지! — final 불가, 테스트 어려움, 순환 참조 감지 불가
    private ArticleRepository articleRepository;
}
```

---

## 14. 코드 포맷 (Spotless)

프로젝트는 **Google Java Format**을 사용합니다. Spotless 플러그인이 자동으로 포맷합니다.

```bash
./gradlew spotlessApply    # 포맷 자동 적용
./gradlew spotlessCheck    # 포맷 검사만 (CI용)
```

> Spotless가 import 순서 정리 + 미사용 import 제거 + 코드 포맷을 모두 처리합니다. 수동으로 포맷을 맞출 필요 없습니다.

---

## 15. 커밋 전 체크리스트

```bash
./gradlew spotlessApply    # 코드 포맷
./gradlew build            # 빌드 + 테스트 통과 확인
```

**두 단계 모두 통과해야 커밋 가능합니다.**

---

## 16. 페이지네이션 응답 (Sprint 2~)

> **주의: 이 설정은 Sprint 2 시작 시 추가합니다. 현재 `build.gradle`과 코드에는 없습니다.**

`Page<T>`를 그대로 직렬화하면 내부 필드가 노출되므로, **Spring 공식 VIA_DTO 모드**를 사용합니다.

### 설정 (전체 한 줄)

```java
// config/WebConfig.java
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig {}
```

### Controller에서 Page 반환

```java
@GetMapping
public Page<ArticleResponse> getArticles(Pageable pageable) {
    return articleService.getArticles(pageable);
}
```

### 자동 생성되는 JSON 응답

```json
{
  "content": [
    { "id": "...", "title": "...", "content": "..." }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 50,
    "totalPages": 5
  }
}
```

### 쿼리 파라미터

| 파라미터 | 타입 | 기본값 | 예시 |
|---------|------|-------|------|
| `page` | int | 0 | `?page=2` |
| `size` | int | 20 | `?size=10` |
| `sort` | String | - | `?sort=createdAt,desc` |

> 커스텀 `PageResponse`, `PageInfo` 클래스를 직접 만들 필요 없습니다. Spring이 알아서 변환합니다.

---

## 17. API 문서화 — Springdoc OpenAPI (Sprint 2~)

> **주의: 이 의존성은 Sprint 2 시작 시 추가합니다. 현재 `build.gradle`에 없습니다.**

### 의존성

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'
```

> `3.0.x`는 Milestone(실험) 버전 — 정식 릴리스된 `2.8.6`을 사용합니다.

추가 설정 없이 자동으로 제공:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API 문서 JSON: `http://localhost:8080/v3/api-docs`

### 어노테이션 사용 규칙

| 어노테이션 | 위치 | 용도 |
|-----------|------|------|
| `@Tag(name = "기사 분석")` | Controller 클래스 | Swagger UI 그룹명 |
| `@Operation(summary = "기사 분석 요청")` | Controller 메서드 | 엔드포인트 설명 |
| `@ApiResponse(responseCode = "201")` | Controller 메서드 | 응답 코드 문서화 |
| `@Parameter(description = "기사 ID")` | 메서드 파라미터 | PathVariable/RequestParam 설명 |
| `@Schema(description = "뉴스 URL")` | DTO 필드 | 모델 필드 설명 |

### 예시

```java
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "기사 분석", description = "뉴스 기사 팩트체크 분석 API")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "기사 분석 요청", description = "URL을 입력하면 팩트체크 분석을 시작합니다")
    @ApiResponse(responseCode = "201", description = "분석 세션 생성 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 URL")
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(
            @Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analysisService.analyze(request));
    }
}
```

### Spring Security 연동 시 (JWT 인증 추가 후)

`SecurityConfig`에서 Swagger 경로를 열어야 합니다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    // ...
)
```

---

## 18. 커밋 메시지

Gitmoji + 태그(scope) 방식:

```
✨feat(entity): Member Entity 작성
🐛fix(service): NPE 발생 수정
♻️refactor(controller): 응답 형식 통일
✅test(repository): MemberRepository 테스트 추가
📝docs: API 명세 업데이트
```
