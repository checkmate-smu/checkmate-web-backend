# DDD/TDD Seed 가이드 — CheckMate Backend

> 최종 갱신: 2026-05-01
> 트리거 phase: `feat/ddd-tdd-seed` (PR 머지 후 dev → main)
> 원본 reference: `Team-project/.plans/checkmate-backend-ddd-tdd-application/HANDOFF.md`
> 상위 PLAN: `checkmate-web/.plans/ddd-tdd-seed/PLAN.md`

이 문서는 CheckMate 백엔드에 도입된 **최소 DDD/TDD 패턴**을 팀원이 이해하고 새 코드에 일관되게 적용하기 위한 가이드다. spring-template Phase E0 PR #22 (`5f1acf54`) 패턴 중 **Spring Boot 3.5.13에 정합한 시드만** 발췌.

---

## 1. 한 줄 정리

| 도입한 것 | 위치 | 효과 |
|----------|------|-----|
| `@ServiceConnection` Testcontainers base | `src/test/java/com/checkmate/web/support/AbstractIntegrationTest.java` | 통합 테스트가 외부 Supabase 의존 없이 PostgreSQL 컨테이너로 격리 실행 |
| Article 도메인 invariant + 정적 팩토리 | `src/main/java/com/checkmate/web/entity/Article.java#extract` | DDD "always-valid" 모델 시범 — invalid 인스턴스 메모리 잔존 차단 |
| Entity setter 금지 ArchUnit 룰 | `src/test/java/com/checkmate/web/architecture/ArchitectureTest.java#entityShouldNotExposeSetters` | 익명(anemic) 회귀 자동 차단 |

---

## 2. 통합 테스트 작성법 (Testcontainers)

### Before — Supabase 직접 의존 (현재 잔존 패턴, 신규 작성 금지)

```java
@SpringBootTest
@ActiveProfiles("test")  // application-test.yml로 DataSource 자체 비활성
class FooIntegrationTest { ... }
```

→ DB 의존 검증을 못 함. context는 뜨지만 JPA 동작은 보장 안 됨.

### After — `AbstractIntegrationTest` 상속

```java
import com.checkmate.web.support.AbstractIntegrationTest;

class FooIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private FooRepository fooRepository;

  @Test
  void DB와_실제로_상호작용하는_테스트() {
    // Testcontainers Postgres가 자동 기동되어 spring.datasource.* 주입됨
    // ddl-auto=create-drop로 entity → 테이블 자동 생성
    fooRepository.save(...);
    assertThat(fooRepository.findAll()).hasSize(1);
  }
}
```

### Pre-condition

- **Docker Desktop 실행 중**이어야 함. 없으면 통합 테스트만 실패하고 단위 테스트는 영향 없음
- 첫 실행 시 `postgres:16-alpine` 이미지 풀(~수십 초). 이후는 캐시
- CI 머신에 Docker 미장착 시 `./gradlew test --tests "*Test" --exclude-tests "*IntegrationTest"` 형태로 분리

---

## 3. 도메인 invariant 작성법 (DDD always-valid)

### Before — anemic entity (모든 필드 builder로 무검증 주입)

```java
// ⚠️ 이제 @Builder(access = PRIVATE)로 막혀 있어 컴파일조차 안 된다 — 아래는 과거 anti-pattern 시각화용
Article article = Article.builder()
    .url("ftp://invalid")  // invalid url이 통과 (이전 anemic 모델에서)
    .build();
```

### After — 정적 팩토리 + invariant

```java
Article article = Article
    .extract("https://example.com/news/123", title, body, lang, domain) // url validation 강제
    .attachTo(session);                                                 // 세션 부착은 별도 invariant
```

내부:

```java
public static Article extract(String url, ...) {
  validateUrl(url);  // 실패 시 IllegalArgumentException
  return Article.builder().url(url).extractedAt(LocalDateTime.now())....build();
}

private static void validateUrl(String url) {
  if (url == null || url.isBlank()) throw new IllegalArgumentException("...");
  if (!url.startsWith("http://") && !url.startsWith("https://")) throw ...;
}
```

### TDD 사이클 (red → green → refactor)

1. **Red**: `ArticleTest`에 invariant 위반 케이스를 `assertThatThrownBy`로 작성 → 실행하면 실패
2. **Green**: entity에 검증 로직 추가 → 통과
3. **Refactor**: 중복 검증을 ValueObject로 추출 (다음 phase)

### `@Builder`는 어떻게?

여전히 builder는 사용 가능. 하지만 **신규 도메인 메서드 작성 시 정적 팩토리 우선**. builder는 JPA proxy/lazy 반환에 필요한 호환성 보존용.

---

## 4. ArchUnit DDD 룰 — Entity setter 금지

```java
@ArchTest
static final ArchRule entityShouldNotExposeSetters =
    noMethods()
        .that().areDeclaredInClassesThat().resideInAPackage("com.checkmate.web.entity")
        .should().haveNameStartingWith("set");
```

- `@Setter` 사용 시 자동 실패
- 변경은 비즈니스 메서드 (`article.markAsTranslated()`, `claim.attachVerification(...)` 등)로
- enum 패키지(`..entity.enums..`)는 별도라 영향 없음

---

## 5. 적용하지 않은 것 (의도적 deferred)

| 항목 | 사유 | 트리거 |
|------|------|------|
| jMolecules `@AggregateRoot` / `@DomainEvent` | 의존성 + 학습 비용. 도메인 복잡도 평가 후 | Sprint 3+ AnalysisSession 라이프사이클 확장 시 |
| Spring Modulith `@ApplicationModule` | 현재 layer-based 패키지 → 모든 top-level package 어노테이션 필요 (R3) | 패키지 재구조화 (도메인별 분리) 결정 후 |
| ArchUnit jMolecules R13-R16 | jMolecules 도입 의존 | 위와 동일 |
| `@TransactionalEventListener(AFTER_COMMIT)` | Spring Events 사용처 0건 | event-driven 도메인 도입 시 |
| `saveAndFlush + try/finally` | Repository impl 클래스 부재 | Custom Repository 도입 시 |

---

## 6. 다음 단계 권장 순서

1. **새 entity 작성 시**: `@Setter` 금지, 비즈니스 메서드 사용, 가능하면 정적 팩토리로 invariant 강제
2. **Repository 통합 테스트 작성 시**: `AbstractIntegrationTest` 상속
3. **다른 도메인 (Claim, VerificationResult)에 invariant 추가** 시: Article 패턴 그대로 복제
4. **Service 레이어 TDD 추가**: Classicist 스타일 — Mockito 최소화, Testcontainers + 실제 Repository 사용

---

## 참조

- 원본 패턴: `Team-project/llm-setup-templates/spring-template` PR #22 (Phase E0)
- Phase E0 학습 박제: 메모리 `project_phase_e0_pilot_done.md`
- DDD/TDD 옵시디언 노트: `Sources/sw-engineering/cases/` N1-N6 + P2
- 도메인 모델: `docs/guides/domain.md`
