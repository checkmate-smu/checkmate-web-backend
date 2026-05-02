# Backend DDD/TDD Aggregate Template

> Phase 21에서 검증된 DDD/TDD seed 패턴을 Sprint 2+ 신규 aggregate에 재사용하기 위한 reference template.
> reference 코드: `src/main/java/com/checkmate/web/entity/Article.java` (PR #27 + #28 + #29 머지)

## 1. Aggregate root 작성 패턴

### 핵심 원칙

| 원칙 | 강제 방법 |
|---|---|
| 인스턴스 생성은 정적 팩토리 메서드로만 | `private constructor` + `Article.extract(...)` 같은 정적 메서드 |
| 비즈니스 invariant은 정적 팩토리에서 검증 | URL pattern / null 체크 → throw `InvariantViolationError` |
| 상태 전환은 비즈니스 메서드로만 | `attachTo(sessionId)` 같은 의도 명시 메서드 (직접 setter 금지) |
| 재호출 invariant은 명시적 throw | `IllegalStateException` → ExceptionHandler에서 409 매핑 |
| ArchUnit 룰 강제 | `entityShouldNotExposeSetters` 통과 의무 |

### Article reference 코드 (현 BE)

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Article extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String url;

    private String title;
    private String body;
    private String lang;

    @Column(name = "domain", length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    private ArticleStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private AnalysisSession session;

    /**
     * 정적 팩토리 — URL invariant + 필수 필드 검증.
     */
    public static Article extract(String url, String title, String body, String lang, String domain) {
        validateUrl(url);
        return Article.builder()
            .url(url)
            .title(title)
            .body(body)
            .lang(lang)
            .domain(domain)
            .status(ArticleStatus.EXTRACTED)
            .build();
    }

    /**
     * 비즈니스 메서드 — 1회 부착 invariant.
     * 재부착 시 IllegalStateException → ConflictException(409) 매핑.
     */
    public Article attachTo(AnalysisSession session) {
        if (this.status == ArticleStatus.ATTACHED) {
            throw new IllegalStateException(
                "Article은 이미 분석 세션에 부착되어 있습니다 (1회 부착 invariant)"
            );
        }
        this.session = session;
        this.status = ArticleStatus.ATTACHED;
        return this;
    }

    private static void validateUrl(String url) {
        if (url == null || !url.matches("^https?://.+")) {
            throw new IllegalArgumentException("URL은 http(s)로 시작해야 합니다");
        }
    }
}
```

## 2. Sprint 2+ 신규 aggregate 작성 단계

### Step 1. Entity 클래스 신규 (5분)

```bash
# 위치
src/main/java/com/checkmate/web/entity/{NewEntity}.java
```

체크리스트:
- [ ] `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder(access = PRIVATE)` + `@AllArgsConstructor(access = PRIVATE)` 4개 어노테이션
- [ ] `extends BaseTimeEntity`
- [ ] 모든 관계 `fetch = FetchType.LAZY`
- [ ] Enum 필드 `@Enumerated(EnumType.STRING)`
- [ ] `@Setter` / `@Data` / `@ToString` 미사용

### Step 2. 정적 팩토리 메서드 작성 (10분)

```java
public static {NewEntity} create({필수 인자}) {
    validate{필수 invariant}();  // null / pattern / 범위 체크
    return {NewEntity}.builder()
        .{필드들}
        .{초기 status}
        .build();
}
```

체크리스트:
- [ ] 비즈니스 의미 명확한 메서드명 (`create` 대신 `extract` / `register` / `submit` 등)
- [ ] invariant 검증을 정적 메서드 진입에서 수행
- [ ] 검증 실패 시 `IllegalArgumentException` (400) 또는 도메인 전용 예외

### Step 3. 비즈니스 메서드 작성 (10분)

```java
public {NewEntity} {action}({인자}) {
    if ({invariant 위반 조건}) {
        throw new IllegalStateException("{한국어 사용자 안내}");
    }
    this.{필드 변경};
    return this;
}
```

체크리스트:
- [ ] 메서드명 = 도메인 의도 (`attachTo` / `complete` / `cancel` 등)
- [ ] invariant 위반은 `IllegalStateException` throw → 409 매핑
- [ ] return type = `this` (메서드 chaining 가능)

### Step 4. ArchUnit 룰 통과 확인 (1분)

```bash
./gradlew test --tests "ArchitectureTest"
```

실패 시: `@Setter` / `@Data` 사용 여부 확인 → 제거.

### Step 5. 단위 테스트 작성 (15분)

```bash
# 위치
src/test/java/com/checkmate/web/entity/{NewEntity}Test.java
```

7+ test cases 의무:
1. `create_validInput_returnsEntity` — happy path
2. `create_nullInput_throws` — invariant 1
3. `create_invalidPattern_throws` — invariant 2
4. `{action}_validState_transitions` — happy state transition
5. `{action}_invalidState_throws` — invariant 위반 (재호출 거부 등)
6. `{action}_chained_returnsThis` — chaining 가능
7. (Bonus) Edge case (empty string / max length 등)

### Step 6. Repository 신규 (5분)

```java
public interface {NewEntity}Repository extends JpaRepository<{NewEntity}, UUID> {
    Optional<{NewEntity}> findByXxx(String xxx);
}
```

체크리스트:
- [ ] `@Repository` 어노테이션 (Spring 자동 인식 시 생략 가능, 명시 권장)
- [ ] 메서드명은 Spring Data JPA convention 따름 (`findBy{Field}`, `existsBy{Field}` 등)

### Step 7. Service 작성 (15분)

```java
@Service
@RequiredArgsConstructor
public class {NewEntity}Service {
    private final {NewEntity}Repository repository;

    @Transactional(readOnly = true)
    public {NewEntity}Response findById(UUID id) {
        {NewEntity} entity = repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("{NewEntity} not found: " + id));
        return {NewEntity}Converter.toResponse(entity);
    }

    @Transactional
    public {NewEntity}Response {action}(UUID id, {action}Request request) {
        {NewEntity} entity = repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("{NewEntity} not found: " + id));
        entity.{action}(request.{필드}());  // 비즈니스 메서드 호출 (dirty checking 활용)
        return {NewEntity}Converter.toResponse(entity);
    }
}
```

체크리스트:
- [ ] `@Transactional` (쓰기) / `@Transactional(readOnly = true)` (읽기)
- [ ] `@RequiredArgsConstructor` + `private final` 생성자 주입
- [ ] Entity 직접 반환 X → Response DTO 변환
- [ ] 비즈니스 메서드 호출은 dirty checking 의존 (명시적 save() 호출 불필요)

### Step 8. Controller 작성 (15분)

상세: `docs/guides/backend-controller-template.md` (별도 작성 예정).

체크리스트:
- [ ] URL: `/api/v1/{entities}` 복수형
- [ ] `@Tag` + `@Operation` + `@ApiResponse` Springdoc 어노테이션
- [ ] 통합 테스트 5+ cases (happy / 404 / 400 / 409 / type mismatch)

## 3. ExceptionHandler 매핑 (이미 작성됨, 참고만)

| 도메인 예외 | HTTP Status | 응답 |
|---|---|---|
| `IllegalArgumentException` | 400 | `ApiErrorResponse { status: "fail", statusCode: 400, message }` |
| `EntityNotFoundException` | 404 | 동일 |
| `IllegalStateException` | 409 | `ConflictException(409)` 매핑 |
| `MethodArgumentTypeMismatchException` | 400 | type mismatch (invalid UUID PathVariable 등) |
| `HttpMessageNotReadableException` | 400 | malformed JSON body |

## 4. 학습 가치 박제 (Phase 21 → Sprint 2+)

본 패턴이 해결하는 것:

1. **lifecycle invariant 시각화** — aggregate가 자기 상태 보호 (재부착 거부 등)
2. **silent corruption 방지** — Enum ORDINAL 금지 + setter 금지로 무지성 변경 차단
3. **일관된 예외 → HTTP status 매핑** — 모든 도메인 예외가 같은 ApiErrorResponse shape으로 응답
4. **테스트 가능한 도메인** — 정적 팩토리 + 비즈니스 메서드 → JPA 컨텍스트 없이 단위 테스트 가능

## 5. 안티패턴 (절대 금지)

```java
// ❌ 금지 1: setter
article.setStatus(ArticleStatus.ATTACHED);
article.setSession(session);

// ✅ 올바름
article.attachTo(session);

// ❌ 금지 2: public constructor
public Article(String url, ...) { ... }
new Article("https://...", ...);

// ✅ 올바름 (private constructor + 정적 팩토리)
Article.extract(url, ...);

// ❌ 금지 3: invariant 검증 부재
public static Article extract(String url) {
    return Article.builder().url(url).build();  // URL invariant 검증 X
}

// ✅ 올바름
public static Article extract(String url) {
    validateUrl(url);  // throw if invalid
    return Article.builder().url(url).build();
}

// ❌ 금지 4: ORDINAL Enum
@Enumerated(EnumType.ORDINAL)  // 컬럼 추가/순서 변경 시 데이터 손상
private ArticleStatus status;

// ✅ 올바름
@Enumerated(EnumType.STRING)
private ArticleStatus status;
```

## 6. 참고 PR (실제 패턴 적용 사례)

- PR #27 (`73b5e43c`): Article DDD/TDD seed — `extract()` + URL invariant + `attachTo()` + ArchUnit 룰
- PR #28 (`d9b6168`): ArticleController + DTOs + Service + 통합 테스트 5 cases + ExceptionHandler 매핑
- PR #29 (`6ad70ec`): AnalysisResponse에 articleId 노출 (FE attach wiring unblock)

## 7. 다음 단계

- BE #22 DataSourceAdapter — `docs/guides/backend-datasource-adapter.md` (PM scaffold reference, 작성 예정)
- BE #24 UrlInputAdapter — `docs/guides/backend-url-input-adapter.md` (Article.extract 사용 reference, 작성 예정)
- ErrorCode Sprint 2 채택 — 5/5 회의 결정 후 `docs/guides/backend-error-handling.md` 신규
